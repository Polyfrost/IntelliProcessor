package org.polyfrost.intelliprocessor.utils

import java.nio.file.Path

data class SourceSetFile(
    val sourceSetName: String,
    val language: String,
    val classPath: Path,
    val subVersion: String?, // If null, this is the mainVersion in project/src/
    private val mainVersion: String,
    private val rootDirectory: Path,
) {
    val isNonGenerated = this.subVersion == null ||
            toOverridePath()?.let { rootDirectory.resolve(it).toFile().exists() } == true

    val displayVersion = subVersion ?: mainVersion

    // Used to sort entries as 1.8.9 will order before 1.12.2 otherwise
    val versionInt = displayVersion.split('-').let { platform ->
        // Convert semantic version to the preprocessor int: 1.21.2 -> 12102
        fun List<String>.getOrZero(index: Int) = getOrNull(index)?.toIntOrNull() ?: 0
        val semVer = platform[0].split('.')
        semVer.getOrZero(0) * 10000 + semVer.getOrZero(1) * 100 + semVer.getOrZero(2)
    }

    // Simpler search key used to streamline keyboard navigation via search, 1.21.2-fabric -> 12102fabric
    private val simpleVersion = "$versionInt${displayVersion.split('-')[1]}"

    fun toRelativePath(): Path {
        return if (subVersion == null) {
            Path.of("src", sourceSetName, language).resolve(classPath)
        } else {
            Path.of("versions", subVersion, "build", "preprocessed", sourceSetName, language).resolve(classPath)
        }
    }

    // Refers to the path of a possible, non-generated, overriding source file in versions/<subVersion>/src/
    fun toOverridePath(): Path? {
        return if (subVersion == null) {
            null
        } else {
            Path.of("versions", subVersion, "src", sourceSetName, language).resolve(classPath)
        }
    }

    fun navigatePath(): Path {
        return if (isNonGenerated) {
            toOverridePath() ?: toRelativePath()
        } else {
            toRelativePath()
        }
    }

    override fun toString(): String {
        val displayFile = classPath.last()
        val srcMark = if (subVersion == null) "(main)"
            else if (isNonGenerated) "(override)"
            else ""

        // e.g. [12102fabric] | 1.21.2-fabric | MyClass.java (main)
        return "[$simpleVersion] | $displayVersion | $displayFile $srcMark"
    }

}
