import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellij)
    alias(libs.plugins.kotlinter)
	alias(libs.plugins.changelog)
}

group = properties("pluginGroup").get()
version = properties("pluginVersion").get()

repositories {
    maven("https://repo.polyfrost.org/releases")
}

kotlin {
    jvmToolchain(17)
}

kotlinter {
    ignoreFailures = false
    reporters = arrayOf("checkstyle", "plain")
}

intellij {
    pluginName = properties("pluginName")
    version = properties("platformVersion")
	type = properties("platformType")

    plugins = properties("platformPlugins").map {
        it.split(',').map(String::trim).filter(String::isNotEmpty)
    }
}

changelog {
	groups.empty()
	repositoryUrl = properties("pluginRepositoryUrl")
}

tasks {
    wrapper {
        gradleVersion = properties("gradleVersion").get()
    }

    patchPluginXml {
        version = properties("pluginVersion")
        sinceBuild = properties("pluginSinceBuild")
        untilBuild = properties("pluginUntilBuild")

		pluginDescription = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
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
		changeNotes = properties("pluginVersion").map { pluginVersion ->
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

	runIdeForUiTests {
		systemProperty("robot-server.port", "8082")
		systemProperty("ide.mac.message.dialogs.as.sheets", "false")
		systemProperty("jb.privacy.policy.text", "<!--999.999-->")
		systemProperty("jb.consents.confirmation.enabled", "false")
	}

	signPlugin {
		certificateChain = environment("CERTIFICATE_CHAIN")
		privateKey = environment("PRIVATE_KEY")
		password = environment("PRIVATE_KEY_PASSWORD")
	}

	publishPlugin {
		dependsOn("patchChangelog")
		token = environment("PUBLISH_TOKEN")
		channels = properties("pluginVersion").map {
			listOf(it.substringAfter("-", "").substringBefore(".").ifEmpty {
				"default"
			})
		}
	}
}
