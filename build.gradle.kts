import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

fun properties(key: String): String = providers.gradleProperty(key).get()

val platformType = properties("platformType")
val platformVersion = properties("platformVersion")
val treeSitterWasmTarget = "wasm32-wasip1"
val treeSitterWasmProjectDirectory = layout.projectDirectory.dir("tree-sitter-wasm")
val treeSitterWasmBuildOutputFile = treeSitterWasmProjectDirectory.file(
    "target/$treeSitterWasmTarget/release/tree_sitter_wasm.wasm"
)
val treeSitterWasmResourceDirectory = layout.projectDirectory.dir("src/main/resources/wasm/tree-sitter")
val treeSitterWasmResourceFile = treeSitterWasmResourceDirectory.file("tree-sitter.wasm")
val skipWasmBuild = providers.gradleProperty("skipWasmBuild").map(String::toBoolean).orElse(false)
val cargoExecutable = resolveRustToolExecutable(
    toolName = "cargo",
    configuredPath = providers.gradleProperty("cargoExecutable").orNull,
)
val rustupExecutable = resolveRustToolExecutable(
    toolName = "rustup",
    configuredPath = providers.gradleProperty("rustupExecutable").orNull,
)

fun resolveRustToolExecutable(
    toolName: String,
    configuredPath: String?,
): String {
    if (!configuredPath.isNullOrBlank()) {
        return configuredPath
    }

    val operatingSystemName = System.getProperty("os.name")
    val executableFileName =
        if (operatingSystemName.startsWith("Windows", ignoreCase = true)) "$toolName.exe" else toolName
    val cargoHomeExecutable = File(System.getProperty("user.home"), ".cargo/bin/$executableFileName")

    return if (cargoHomeExecutable.isFile && cargoHomeExecutable.canExecute()) {
        cargoHomeExecutable.absolutePath
    } else {
        toolName
    }
}

fun captureCommandOutput(
    workingDirectory: File,
    vararg arguments: String,
): String {
    val process = ProcessBuilder(*arguments)
        .directory(workingDirectory)
        .redirectErrorStream(true)
        .start()
    val outputText = process.inputStream.bufferedReader().use { it.readText() }.trim()
    val exitCode = process.waitFor()

    if (exitCode != 0) {
        throw GradleException(
            "Command `${arguments.joinToString(" ")}` failed with exit code $exitCode.\n$outputText"
        )
    }

    return outputText
}

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Gradle Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(17)
}

// Configure project's dependencies
repositories {
    mavenCentral()

    // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
    intellijPlatform {
        defaultRepositories()

    }
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {
    // Json Library
    implementation(libs.jacksonDatabind)
    implementation(libs.jacksonModuleKotlin)
    implementation(libs.jsonPath)
    implementation(libs.jmespathJackson)
    implementation(libs.dataFaker)
    implementation(libs.jsonSchemaValidator)
    implementation(libs.chicoryRuntime)
    implementation(libs.chicoryWasi)

    implementation(libs.jacksonJq)

    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        create(
            type = IntelliJPlatformType.fromCode(platformType),
            version = platformVersion
        )

        // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map<List<String>> {
            it.split(',')
        })


        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
        plugins(providers.gradleProperty("platformPlugins").map<List<String>> {
            it.split(',')
        })

        javaCompiler()
        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }
}

val checkWasmPrerequisites by tasks.registering {
    group = "wasm"
    description = "Verify Rust and WASM build prerequisites"
    notCompatibleWithConfigurationCache("Runs local toolchain discovery commands during task execution.")

    doLast {
        try {
            captureCommandOutput(rootDir, cargoExecutable, "--version")
        } catch (exception: Exception) {
            throw GradleException(
                "cargo is required to build tree-sitter WASM. Install Rust with rustup first.",
                exception,
            )
        }

        val installedTargets = try {
            captureCommandOutput(rootDir, rustupExecutable, "target", "list", "--installed")
        } catch (exception: Exception) {
            throw GradleException(
                "rustup is required to verify the $treeSitterWasmTarget target.",
                exception,
            )
        }

        if (installedTargets.lineSequence().none { it.trim() == treeSitterWasmTarget }) {
            throw GradleException(
                "Rust target $treeSitterWasmTarget is not installed. " +
                    "Run `rustup target add $treeSitterWasmTarget` first."
            )
        }
    }
}

val buildTreeSitterWasm by tasks.registering(Exec::class) {
    group = "wasm"
    description = "Build tree-sitter WASM module from Rust source"
    notCompatibleWithConfigurationCache("Invokes a local Rust build and captures script-level task inputs.")
    dependsOn(checkWasmPrerequisites)
    onlyIf { !skipWasmBuild.get() }

    workingDir = treeSitterWasmProjectDirectory.asFile
    commandLine(cargoExecutable, "build", "--target", treeSitterWasmTarget, "--release")

    inputs.files(fileTree(treeSitterWasmProjectDirectory.dir("src")))
    inputs.files(
        treeSitterWasmProjectDirectory.file("Cargo.toml"),
        treeSitterWasmProjectDirectory.file("Cargo.lock"),
        treeSitterWasmProjectDirectory.file("build.rs"),
        treeSitterWasmProjectDirectory.file(".cargo/config.toml"),
    )
    outputs.file(treeSitterWasmBuildOutputFile)
}

val copyWasmToResources by tasks.registering(Copy::class) {
    group = "wasm"
    description = "Copy built tree-sitter WASM module to plugin resources"
    notCompatibleWithConfigurationCache("Uses script-level file providers for generated WASM resources.")
    dependsOn(buildTreeSitterWasm)
    onlyIf { !skipWasmBuild.get() }

    from(treeSitterWasmBuildOutputFile)
    into(treeSitterWasmResourceDirectory)
    rename { "tree-sitter.wasm" }

    inputs.file(treeSitterWasmBuildOutputFile)
    outputs.file(treeSitterWasmResourceFile)

    doFirst {
        if (!treeSitterWasmBuildOutputFile.asFile.exists()) {
            throw GradleException(
                "Expected WASM build output not found: ${treeSitterWasmBuildOutputFile.asFile.absolutePath}"
            )
        }
    }
}

if (!skipWasmBuild.get()) {
    tasks.named("processResources") {
        dependsOn(copyWasmToResources)
    }
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    autoReload = true

    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")


        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = providers.gradleProperty("pluginVersion")
            .map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

// Configure Gradle Kover Plugin - read more: https://github.com/Kotlin/kotlinx-kover#configuration
kover {
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    publishPlugin {
        dependsOn(patchChangelog)
    }

    // -----------------------------------------------------------
    // Custom Deep Clean Task
    // Usage: ./gradlew deepClean --no-daemon
    // -----------------------------------------------------------
    register<Task>("deepClean") {
        group = "build"
        description = "Deletes build directory, local .gradle directory, and attempts to clear global Gradle caches."
        doLast {
            // 1. Clean Project Build Directory (Standard clean)
            val buildDir = layout.buildDirectory.get().asFile
            if (buildDir.exists()) {
                buildDir.deleteRecursively()
                println("[Clean] Deleted project build directory: ${buildDir.absolutePath}")
            }

            // 2. Clean Project Local .gradle Directory
            val projectDotGradle = file(".gradle")
            if (projectDotGradle.exists()) {
                projectDotGradle.deleteRecursively()
                println("[Clean] Deleted project .gradle directory: ${projectDotGradle.absolutePath}")
            }

            // 3. Clean Global ~/.gradle/caches (Use with caution)
            val userHome = System.getProperty("user.home")
            val globalCaches = File(userHome, ".gradle/caches")

            println("\n[Warning] Attempting to delete global Gradle caches at: ${globalCaches.absolutePath}")
            println("Note: This may fail if the Gradle Daemon is running. Use '--no-daemon' to improve success rate.")

            if (globalCaches.exists()) {
                try {
                    // Try to delete. This might throw exception if files are locked.
                    globalCaches.deleteRecursively()
                    println("[Clean] Successfully deleted global Gradle caches.")
                } catch (e: Exception) {
                    println("[Error] Failed to fully delete global caches (files might be locked): ${e.message}")
                    println("Tip: Try running './gradlew deepClean --no-daemon'")
                }
            } else {
                println("[Clean] Global cache directory does not exist or was already removed.")
            }
        }
    }
}

intellijPlatformTesting {
    runIde {
        register("runIdeForUiTests") {
            task {
                jvmArgumentProviders += CommandLineArgumentProvider {
                    listOf(
                        "-Drobot-server.port=8082",
                        "-Dide.mac.message.dialogs.as.sheets=false",
                        "-Djb.privacy.policy.text=<!--999.999-->",
                        "-Djb.consents.confirmation.enabled=false",
                    )
                }
            }

            plugins {
                robotServerPlugin()
            }
        }
    }
}
