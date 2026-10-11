plugins {
    kotlin("jvm") version "2.4.0"
    application
}

group = "org.jetbrains.edu.sed2026"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin PSI parser, used to extract the structure of Kotlin source files.
    implementation(kotlin("compiler-embeddable"))
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "org.jetbrains.edu.aads2026.MainKt"
}

tasks.named<JavaExec>("run") {
    // The client reads queries from the console.
    standardInput = System.`in`
}

tasks.test {
    useJUnitPlatform()
}
