


plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    application
}

group = "ru.sem.ai.challenge"
version = "1.0-SNAPSHOT"



sourceSets.main {
    java.srcDir(layout.buildDirectory.dir("generated/src/main/kotlin"))
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    // Корутины
    /*implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")*/

    implementation("io.ktor:ktor-client-core:3.0.2")
    implementation("io.ktor:ktor-client-cio:3.0.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.2")

    implementation("io.ktor:ktor-server-netty:3.0.2")

    implementation("io.modelcontextprotocol:kotlin-sdk:0.8.1")
    implementation("io.modelcontextprotocol:kotlin-sdk-client:0.8.1")
    implementation("io.modelcontextprotocol:kotlin-sdk-server:0.8.1")

    // Kotlinx serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("org.slf4j:slf4j-simple:2.0.12")
    implementation("io.ktor:ktor-client-logging:3.0.3")
}

tasks.test {
    useJUnitPlatform()
}

// 👇 НОВЫЙ БЛОК: Правильное место для jvmToolchain
kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("MainKt")
}