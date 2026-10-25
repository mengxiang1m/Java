plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.cst3492"
version = "0.1.0"

apply(from = "gradle/rag-dependencies.gradle.kts")

val localIdePath = providers.gradleProperty("localIdePath")
    .orElse("D:/IntelliJ/IntelliJ IDEA 2026.2.2")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // Required by the main branch's module architecture.
    compileOnly("com.google.code.gson:gson:2.10.1")
    testImplementation("com.google.code.gson:gson:2.10.1")

    intellijPlatform {
        local(localIdePath)
    }
}

tasks.test {
    useJUnitPlatform {
        if (providers.gradleProperty("runLlmIntegrationTests").orNull == "true") {
            includeTags("llm-integration")
        } else {
            excludeTags("llm-integration")
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// Preserve the existing Java 21 bytecode target.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
            untilBuild = "262.*"
        }
    }
    pluginVerification {
        ides {
            local(provider { intellijPlatform.platformPath.toFile() })
        }
    }
}
