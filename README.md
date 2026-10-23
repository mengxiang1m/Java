# CST3492 Assistant

A minimal IntelliJ IDEA plugin foundation for our four-person team. It adds a
**CST3492 Assistant** Tool Window containing a welcome message.

## Versions and prerequisites

- The existing **IntelliJ IDEA 2026.2.2** installation at
  `D:\IntelliJ\IntelliJ IDEA 2026.2.2` is the development sandbox and API target.
  Gradle uses the supported `local(...)` dependency; it does not download an IDE.
- **JDK 25** runs Gradle and compiles the plugin with **`--release 21`**, retaining
  Java 21 language, Java SE API, and bytecode limits for our own source. The IDE
  itself requires Java 25 and supplies JetBrains Runtime 25 for the sandbox.
- The official `org.jetbrains.intellij.platform` Gradle plugin is pinned to **2.19.0**.
- The Gradle Wrapper pins **Gradle 9.6.0**. Use a full **JDK 25**
  to run Gradle, and make sure `java -version` works. Set `JAVA_HOME` to that JDK
  if needed. There is no automatic JDK provisioning resolver.
- Gradle and plugin tooling dependencies must be cached or downloadable. No global
  Gradle install or API keys are required.

The local build declares compatibility with the **262 (2026.2)** branch only.
This replaces the original 252 target; Java 21 bytecode alone does not establish
compatibility with older IntelliJ APIs. `runIde` uses the local IDE binaries with
separate sandbox settings. To use another installation of the same platform branch,
append `'-PlocalIdePath=C:/path/to/IntelliJ IDEA'` to each Gradle command.

## Open in IntelliJ IDEA

Open this repository folder as a project and load the Gradle build when prompted.
Use the Wrapper distribution and select JDK 25 as the **Gradle JVM** under
Settings > Build, Execution, Deployment > Build Tools > Gradle. Let sync finish.

## Build and validate (PowerShell)

```powershell
Set-Location 'D:\Desktop\Java'
.\gradlew.bat build
.\gradlew.bat buildPlugin
.\gradlew.bat verifyPluginProjectConfiguration verifyPluginStructure
.\gradlew.bat verifyPlugin
```

`build` compiles and packages the plugin ZIP under `build/distributions/`.
`verifyPluginProjectConfiguration` checks build/platform settings;
`verifyPluginStructure` checks the packaged plugin descriptor and layout.
`verifyPlugin` performs binary compatibility checks against the local sandbox
IDE and may download the JetBrains Plugin Verifier. There are no automated tests
yet; these checks do not replace the UI smoke check below.

## Launch the sandbox (PowerShell)

```powershell
Set-Location 'D:\Desktop\Java'
.\gradlew.bat runIde
```

In the separate sandbox IDE, open or create any project, then select
**View > Tool Windows > CST3492 Assistant**. Confirm that it displays
**Welcome to CST3492 Assistant!** Close the sandbox IDE to finish `runIde`.
Sandbox settings and logs are under
`.intellijPlatform/sandbox/cst3492-assistant/IU-2026.2.2/` and are ignored by Git.

## Verification on this workstation

The original remote 2025.2.5 build was blocked by downloads. The configuration now
uses the verified local IU-262.10315.125 installation and installed JDK 25.0.4.1.
See the actual task results below; a sandbox launch alone is not a UI smoke check.

- `compileJava`, `verifyPluginStructure`, `buildPlugin`, and `build`: successful
  with `--offline`. Package: `build/distributions/cst3492-assistant-0.1.0.zip`.
- `verifyPluginProjectConfiguration`: completed with warnings about the retained
  Java 21 source level being below platform Java 25 and the explicit `262.*`
  upper compatibility bound. These settings are intentional; this is not a
  warning-free validation result. Class-file major version 65 confirms Java 21 bytecode.
- `runIde --offline`: sandbox started with bundled JBR 25.0.4; its log confirms
  `Loaded custom plugins: CST3492 Assistant (0.1.0)`. Startup also reported
  warnings about other bundled IDE plugins and their dependencies. The Tool
  Window's rendered welcome message has not been visually verified.
- `verifyPlugin` (the separate binary compatibility verifier) was not run in this
  local verification pass. Structure/configuration validation is not that check.

Next: in the running sandbox, open a project and check the Tool Window as described
above. Close the sandbox to allow the long-running `runIde` task to finish.

## Project layout and team extension points

```text
build.gradle.kts                         Build, Java toolchain, platform target
settings.gradle.kts                      Project name
gradle/wrapper/ + gradlew + gradlew.bat   Reproducible Gradle entry points
src/main/java/com/cst3492/assistant/ui/
    AssistantToolWindowFactory.java      Member C's Tool Window UI entry point
src/main/resources/META-INF/plugin.xml   Plugin identity and extension registration
```

Keep UI code in `ui`; as other members add features, place their implementation
in separate packages under `com.cst3492.assistant` and agree on small interfaces
between components. Avoid putting application logic into the Tool Window factory.
Use the shared Gradle build; keep personal IDE settings and secrets out of Git.

This foundation includes no retrieval, LLM integration, document ingestion, or
editor context-menu actions. Those features are future team work.

Version references: [JetBrains Gradle plugin documentation](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html),
[platform Java/build compatibility](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html),
and [Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html).
