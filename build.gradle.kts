fun properties(key: String) = providers.gradleProperty(key)

plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellij)
    alias(libs.plugins.kotlinter)
}

group = properties("pluginGroup").get()
version = properties("pluginVersion").get()

repositories {
    maven("https://repo.polyfrost.org/releases")
}

kotlin {
    jvmToolchain(21)
}

kotlinter {
    ignoreFailures = false
    reporters = arrayOf("checkstyle", "plain")
}

intellij {
    pluginName = properties("pluginName")
    version = properties("platformVersion")
    plugins = properties("platformPlugins").map {
        it.split(',').map(String::trim).filter(String::isNotEmpty)
    }
}

tasks {
    wrapper {
        gradleVersion = properties("gradleVersion").get()
    }

    patchPluginXml {
        version = properties("pluginVersion")
        sinceBuild = properties("pluginSinceBuild")
        untilBuild = properties("pluginUntilBuild")
    }
}
