package org.polyfrost.intelliprocessor.utils

import org.polyfrost.intelliprocessor.utils.PreprocessorVersion.Companion.preprocessorVersion
import java.nio.file.Path

data class SourceSetFile(
    val sourceSetName: String,
    val language: String,
    val classPath: Path,
    val subVersion: String?, // If null, this is the mainVersion in project/src/
    private val mainVersion: String,
    private val rootDirectory: Path,
) {
    // Refers to the path of a possible, non-generated, overriding source file in versions/<subVersion>/src/
    private val overridePath: Path? = subVersion?.let {
        Path.of("versions", it, "src", sourceSetName, language).resolve(classPath)
    }

    val isNonGenerated = this.subVersion == null ||
            overridePath?.let { rootDirectory.resolve(it).toFile().exists() } == true

    fun toRelativePath(): Path {
        return if (subVersion == null) {
            Path.of("src", sourceSetName, language).resolve(classPath)
        } else if (isNonGenerated) {
            overridePath!!
        } else {
            Path.of("versions", subVersion, "build", "preprocessed", sourceSetName, language).resolve(classPath)
        }
    }

    var metOpeningCondition = true

    val displayVersion = subVersion ?: mainVersion

    val version = displayVersion.preprocessorVersion ?: PreprocessorVersion.NULL

    override fun toString(): String {
        val displayFile = classPath.last()
        val srcMark = if (subVersion == null) "(main)"
            else if (isNonGenerated) "(override)"
            else ""

        // e.g. [12102fabric] | 1.21.2-fabric | MyClass.java (main)
        return "[${version.mc}${version.loader}] | $displayVersion | $displayFile $srcMark"
    }

}
