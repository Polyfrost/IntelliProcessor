package dev.deftu.intelliprocessor.utils

import java.nio.file.Path

data class SourceSetFile(
    val sourceSetName: String,
    val language: String,
    val classPath: Path,
    val version: String?,
) {

    fun toRelativePath(): Path {
        return if (version == null) {
            Path.of("src", sourceSetName, language).resolve(classPath)
        } else {
            Path.of("versions", version, "build", "preprocessed", sourceSetName, language).resolve(classPath)
        }
    }

}
