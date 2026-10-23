plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.cst3492"
version = "0.1.0"

// Override on another workstation with -PlocalIdePath="C:/path/to/IntelliJ IDEA".
val localIdePath = providers.gradleProperty("localIdePath")
    .orElse("D:/IntelliJ/IntelliJ IDEA 2026.2.2")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        local(localIdePath)
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// Use the installed JDK while retaining Java 21 language, API, and bytecode limits.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
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
