import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatform)
    alias(libs.plugins.kotlinter)
	alias(libs.plugins.changelog)
}

group = properties("pluginGroup").get()
version = properties("pluginVersion").get()

kotlin {
	jvmToolchain(17)
}

repositories {
	mavenCentral()

	intellijPlatform {
		defaultRepositories()
	}
}

dependencies {
	testImplementation(libs.junit)

	intellijPlatform {
		create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
		bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })
		plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

		instrumentationTools()
		pluginVerifier()
		zipSigner()
		testFramework(TestFrameworkType.Platform)
	}
}

intellijPlatform {
	pluginConfiguration {
		version = providers.gradleProperty("pluginVersion")

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
		channels = providers.gradleProperty("pluginVersion").map {
			listOf(it.substringAfter('-', "")
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
	repositoryUrl = properties("pluginRepositoryUrl")
}

kotlinter {
	ignoreFailures = false
	reporters = arrayOf("checkstyle", "plain")
}

tasks {
    wrapper {
        gradleVersion = properties("gradleVersion").get()
    }

	publishPlugin {
		dependsOn("patchChangelog")
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
