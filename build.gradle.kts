import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML

plugins {
    java
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellij.plugin)
    alias(libs.plugins.changelog)
}

group = providers.gradleProperty("plugin.group").get()
version = providers.gradleProperty("plugin.version").get()

repositories {
    mavenCentral()
    maven("https://maven.deftu.dev/releases")
    maven("https://maven.deftu.dev/snapshots")
    mavenLocal()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(libs.versions.intellij.ide, useInstaller = false)
        pluginVerifier()

        // Required
        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.gradle")

        // Optional
        bundledPlugin("org.jetbrains.kotlin")

        testFramework(TestFrameworkType.JUnit5)
        testFramework(TestFrameworkType.Plugin.Java)
    }

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("plugin.version")

        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
			val start = "<!-- plugin description -->"
			val end = "<!-- plugin description end -->"

			with (it.lines()) {
				if (!containsAll(listOf(start, end))) {
					throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
				}
				subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
			}
		}

		val changelog = project.changelog
		changeNotes = providers.gradleProperty("plugin.version").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = providers.gradleProperty("plugin.version").map {
            listOf(
                it.substringAfter('-', "")
                    .substringBefore('.').ifEmpty { "default" })
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

changelog {
	groups.empty()
	repositoryUrl = providers.gradleProperty("plugin.repository.url")
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

extensions.configure<KotlinJvmProjectExtension> {
    jvmToolchain(21)
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradle.version").get()
    }

    publishPlugin {
		dependsOn("patchChangelog")
	}
}
