plugins {
    java
    id("com.gradleup.shadow") version "9.3.1"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "net.democracycraft"
version = "1.0.13"

repositories {
    mavenCentral()
    maven(uri("https://jitpack.io"))
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("net.kyori:adventure-text-minimessage:4.26.1")
}

tasks {
    runServer {

        minecraftVersion("1.21.8")
    }
    shadowJar {
        configurations = listOf(project.configurations.runtimeClasspath.get())

        archiveClassifier.set("")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
