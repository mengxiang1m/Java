plugins {
    id("java")
    // IntelliJ Platform plugin (used to build/run this code as an IntelliJ IDEA plugin).
    // Requires Gradle 8.9+; the wrapper below uses 8.14.
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.cst3492.ta"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // Gson is bundled with the IntelliJ platform at runtime, so it is declared
    // compileOnly here (the plugin does NOT bundle its own copy). It is also a
    // testImplementation so the unit tests can run standalone.
    compileOnly("com.google.code.gson:gson:2.10.1")
    testImplementation("com.google.code.gson:gson:2.10.1")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

intellij {
    version.set("2024.3")
    type.set("IC") // IntelliJ IDEA Community Edition
    // No extra platform plugins are needed by the LLM module.
    plugins.set(listOf())
}

tasks {
    // Compile to Java 17 bytecode no matter which JDK runs Gradle, so the build
    // also works on machines that only have a newer JDK installed.
    withType<JavaCompile>().configureEach {
        options.release.set(17)
    }

    test {
        useJUnitPlatform()
    }
}
