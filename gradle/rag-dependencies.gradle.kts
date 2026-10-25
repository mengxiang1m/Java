// Shared by the IntelliJ plugin and the standalone RAG build.
dependencies {
    add("implementation", "org.apache.pdfbox:pdfbox:3.0.8")
    add("implementation", "org.apache.poi:poi-ooxml:6.0.0")
    add("implementation", "org.apache.poi:poi-scratchpad:6.0.0")

    add("testImplementation", platform("org.junit:junit-bom:5.13.4"))
    add("testImplementation", "org.junit.jupiter:junit-jupiter")
    add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
}
