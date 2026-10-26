plugins {
    java
    application
}

group = "com.cst3492"
version = "0.1.0"

repositories {
    mavenCentral()
}

apply(from = "../gradle/rag-dependencies.gradle.kts")

dependencies {
    // Standalone POI logging uses the JDK logger. The plugin keeps its IDE logging setup.
    runtimeOnly("org.apache.logging.log4j:log4j-to-jul:2.26.1")
}

// Compile the actual plugin sources, not a second implementation or test copy.
sourceSets {
    main {
        java.setSrcDirs(listOf("../src/main/java"))
        java.include("com/cst3492/ta/core/**", "com/cst3492/ta/rag/**")
    }
    test {
        java.setSrcDirs(listOf("../src/test/java"))
        java.include("com/cst3492/ta/core/**", "com/cst3492/ta/rag/**")
        // This suite verifies A alone; the project/chat pipeline belongs to integration-standalone.
        java.exclude("com/cst3492/ta/rag/ProjectKnowledgeBaseIntegrationTest.java")
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    systemProperty("file.encoding", "UTF-8")
}

application {
    mainClass.set("com.cst3492.ta.rag.RagDemo")
}
