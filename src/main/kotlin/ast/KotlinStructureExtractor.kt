// The standalone compiler environment is internal API, but it is the documented way to get Kotlin PSI outside the IDE.
@file:OptIn(CompilerConfiguration.Internals::class, K1Deprecation::class)

package org.jetbrains.edu.aads2026.ast

import org.jetbrains.edu.aads2026.tokenizer.Document
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeParameterListOwner
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Extracts the structure of Kotlin files (`.kt`, `.kts`) with the parser of the Kotlin compiler.
 *
 * Only top-level and member declarations are reported; local declarations inside function bodies
 * are ignored. Associations are reported only for top-level declarations: those of the members,
 * including nested classes, are merged into the top-level declaration that contains them.
 *
 * The extractor works on syntax only, without resolving symbols, therefore:
 *
 * - associations are simple type names as written in the code (`kotlin.io.path.Path` → `Path`);
 * - result types that are not written explicitly (`fun f() = 42`) are unknown and not reported;
 * - names of type parameters (`T`) and well-known standard library types ([ignoredTypes])
 *   are not reported as associations; type arguments are: `Sequence<SearchResult>` → `SearchResult`.
 *
 * Holds a Kotlin compiler environment, so [close] it when done.
 */
class KotlinStructureExtractor(
    private val ignoredTypes: Set<String> = STANDARD_TYPES,
) : StructureExtractor, AutoCloseable {

    private val disposable = Disposer.newDisposable("KotlinStructureExtractor")
    private val psiFactory: KtPsiFactory

    init {
        setIdeaIoUseFallback()
        val configuration = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MODULE_NAME, "corpus")
            put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        }
        val environment = KotlinCoreEnvironment.createForProduction(
            disposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES,
        )
        psiFactory = KtPsiFactory(environment.project, markGenerated = false)
    }

    override fun extract(document: Document, text: String): AstNode? {
        if (!document.name.endsWith(".kt") && !document.name.endsWith(".kts")) return null
        // The PSI parser requires `\n` line separators.
        val ktFile = psiFactory.createFile(document.name, text.replace("\r\n", "\n").replace('\r', '\n'))
        return AstNode(
            name = document.name,
            type = NodeType.FILE,
            children = ktFile.declarations.mapNotNull { convert(it, emptySet())?.withHoistedAssociations() },
        )
    }

    /** Moves the associations of all descendants to this node. */
    private fun AstNode.withHoistedAssociations(): AstNode = copy(
        children = children.map { it.withoutAssociations() },
        associations = walk().flatMapTo(sortedSetOf()) { it.associations }.minus(name).toList(),
    )

    private fun AstNode.withoutAssociations(): AstNode =
        copy(children = children.map { it.withoutAssociations() }, associations = emptyList())

    override fun close() {
        Disposer.dispose(disposable)
    }

    /**
     * @param typeParameters names of the type parameters of the enclosing declarations
     */
    private fun convert(declaration: KtDeclaration, typeParameters: Set<String>): AstNode? {
        val scopeTypeParameters = typeParameters + declaration.typeParameterNames()
        return when (declaration) {
            is KtEnumEntry -> null
            is KtClassOrObject -> convertClass(declaration, scopeTypeParameters)
            is KtNamedFunction -> declaration.name?.let { name ->
                AstNode(name, NodeType.FUNCTION, associations = functionAssociations(declaration, scopeTypeParameters))
            }
            is KtProperty -> declaration.name?.let { AstNode(it, NodeType.PROPERTY) }
            is KtTypeAlias -> declaration.name?.let { name ->
                AstNode(name, NodeType.TYPEALIAS, associations = typeNames(declaration.getTypeReference(), scopeTypeParameters).sorted())
            }
            else -> null
        }
    }

    private fun convertClass(declaration: KtClassOrObject, typeParameters: Set<String>): AstNode {
        val name = declaration.name ?: if (declaration is KtObjectDeclaration && declaration.isCompanion()) "Companion" else "<anonymous>"
        val type = when {
            declaration is KtObjectDeclaration -> NodeType.OBJECT
            declaration is KtClass && declaration.isInterface() -> NodeType.INTERFACE
            declaration is KtClass && declaration.isEnum() -> NodeType.ENUM
            else -> NodeType.CLASS
        }

        // Properties declared in the primary constructor: `class Foo(val bar: Bar)`.
        val constructorProperties = declaration.primaryConstructorParameters
            .filter { it.hasValOrVar() }
            .mapNotNull { it.name }
            .map { AstNode(it, NodeType.PROPERTY) }
        val members = declaration.declarations.mapNotNull { convert(it, typeParameters) }

        // Associations of the members are merged in later, by withHoistedAssociations().
        val associations = sortedSetOf<String>()
        for (entry in declaration.superTypeListEntries) {
            associations += typeNames(entry.typeReference, typeParameters)
        }
        for (constructor in listOfNotNull(declaration.primaryConstructor) + declaration.secondaryConstructors) {
            associations += functionAssociations(constructor, typeParameters)
        }

        return AstNode(name, type, constructorProperties + members, associations.toList())
    }

    /** Types of the receiver, parameters and result of a function or constructor. */
    private fun functionAssociations(function: KtCallableDeclaration, typeParameters: Set<String>): List<String> {
        val typeReferences = listOf(function.receiverTypeReference, function.typeReference) +
            function.valueParameters.map { it.typeReference }
        return typeReferences.flatMapTo(sortedSetOf()) { typeNames(it, typeParameters) }.toList()
    }

    /**
     * Names of all types mentioned in a type reference, including type arguments and the parts of
     * function types: `Map<String, (Analyzer) -> SearchEngine?>` → `Analyzer`, `SearchEngine`.
     */
    private fun typeNames(typeReference: KtTypeReference?, typeParameters: Set<String>): Set<String> {
        if (typeReference == null) return emptySet()
        return typeReference.collectDescendantsOfType<KtUserType>()
            // In `kotlin.io.Path`, the qualifier `kotlin.io` is a KtUserType nested in the KtUserType `Path`.
            .filter { it.parent !is KtUserType }
            .mapNotNull { it.referencedName }
            .filterTo(mutableSetOf()) { it !in typeParameters && it !in ignoredTypes }
    }

    private fun KtDeclaration.typeParameterNames(): List<String> =
        (this as? KtTypeParameterListOwner)?.typeParameters?.mapNotNull { it.name }.orEmpty()

    companion object {
        /** Built-in and common standard library types that carry little information as associations. */
        val STANDARD_TYPES = setOf(
            "Any", "Unit", "Nothing", "Boolean", "Char", "Byte", "Short", "Int", "Long", "Float", "Double",
            "UByte", "UShort", "UInt", "ULong", "Number", "String", "CharSequence", "Comparable",
            "Array", "BooleanArray", "CharArray", "ByteArray", "ShortArray", "IntArray", "LongArray",
            "FloatArray", "DoubleArray", "Iterable", "Iterator", "Collection", "List", "Set", "Map",
            "MutableIterable", "MutableIterator", "MutableCollection", "MutableList", "MutableSet", "MutableMap",
            "ArrayList", "HashMap", "HashSet", "LinkedHashMap", "LinkedHashSet", "Sequence", "Pair", "Triple",
            "Lazy", "Result", "Function", "StringBuilder", "Regex", "Throwable", "Exception", "RuntimeException", "Error",
        )
    }
}
