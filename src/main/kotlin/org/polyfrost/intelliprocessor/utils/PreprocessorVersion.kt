package org.polyfrost.intelliprocessor.utils

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiFile
import java.nio.file.Files
import kotlin.collections.toList
import kotlin.io.path.relativeTo

// Represents a preprocessor comparable minecraft version along with its loader (e.g., fabric, forge).
// Accessed as properties on string-ified preprocessor versions and PsiFiles.
class PreprocessorVersion private constructor(val mc: Int, val loader: String) {
    companion object {
        val NULL = PreprocessorVersion(0, "null")

        val String.preprocessorVersion: PreprocessorVersion? get() {
            val int = sugarToInt(this) ?: return null
            val loader = split("-").getOrNull(1) ?: return null
            return PreprocessorVersion(int, loader)
        }

        val PsiFile.preprocessorVersion: PreprocessorVersion? get() {
            val version = versionStringOfFile ?: return null
            return version.preprocessorVersion
        }

        private val extractFromModule = """\b\d+\.\d+(?:\.\d+)?-\w+\b""".toRegex()
        val PsiFile.versionStringOfFile: String? get() {
            val vf: VirtualFile? = virtualFile
            if (vf == null) { // No backing file
                val module = ModuleUtilCore.findModuleForPsiElement(this) ?: return null
                return extractFromModule.find(module.name)?.value ?: preprocessorMainVersion
            }
            val rootDirectory = findModuleDirForFile(this)?.toPath() ?: return null
            val relPath = vf.toNioPathOrNull()?.relativeTo(rootDirectory)?.toList() ?: return null
            if (relPath[0].toString() != "versions") return preprocessorMainVersion
            return relPath[1].toString()
        }

        val PsiFile.preprocessorMainVersion: String? get() {
            val moduleDir = findModuleDirForFile(this) ?: run {
                println("Module directory could not be found for file: ${virtualFile?.path}")
                return null
            }

            val versionFile = moduleDir.toPath().resolve("versions/mainProject")
            if (!Files.exists(versionFile)) {
                println("Main project version file does not exist at: $versionFile")
                return null
            }

            return Files.readString(versionFile).trim()
        }

        /**
         * Converts a preprocessor version string to an integer.
         * Supports both sugar version format (e.g., `1.16.5`) and integer format (e.g., `11605`).
         */
        fun String.toPreprocessorIntOrNull() : Int? =
            if (this.contains(".")) sugarToInt(this)
            else this.toIntOrNull()


        private val regex = "(?<major>\\d+)\\.(?<minor>\\d+)(?:\\.(?<patch>\\d+))?".toRegex()
        private fun sugarToInt(version: String): Int? {
            val match = regex.find(version) ?: return null
            val groups = match.groups

            val major = groups["major"]?.value?.toInt() ?: return null
            val minor = groups["minor"]?.value?.toInt() ?: return null
            val patch = groups["patch"]?.value?.toInt() ?: 0

            return major * 10000 + minor * 100 + patch
        }
    }
}