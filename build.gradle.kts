fun properties(key: String) = providers.gradleProperty(key)

plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellij)
}

group = properties("pluginGroup").get()
version = properties("pluginVersion").get()

repositories {
    maven("https://repo.polyfrost.cc/releases")
}

kotlin {
    jvmToolchain(17)
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
