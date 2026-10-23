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
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.13.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
    intellijPlatform {
        local(localIdePath)
    }
}

tasks.test {
    useJUnitPlatform()
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
