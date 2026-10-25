plugins { java }

repositories { mavenCentral() }

apply(from = "../gradle/rag-dependencies.gradle.kts")

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    runtimeOnly("org.apache.logging.log4j:log4j-to-jul:2.26.1")
}

// Exercise the real A/B/chat adapters without downloading an IntelliJ SDK.
sourceSets {
    main {
        java.setSrcDirs(listOf("../src/main/java"))
        java.include("com/cst3492/ta/core/**", "com/cst3492/ta/rag/**",
            "com/cst3492/ta/llm/**", "com/cst3492/assistant/chat/**",
            "com/cst3492/assistant/ui/ChatReplyPresentation.java")
    }
    test {
        java.setSrcDirs(listOf("../src/test/java"))
        java.include("com/cst3492/ta/rag/**", "com/cst3492/ta/llm/**",
            "com/cst3492/assistant/chat/**", "com/cst3492/integration/**")
    }
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform { excludeTags("llm-integration") }
}
