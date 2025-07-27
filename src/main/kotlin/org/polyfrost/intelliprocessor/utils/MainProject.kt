package org.polyfrost.intelliprocessor.utils

import com.intellij.psi.PsiFile
import java.nio.file.Files

object MainProject {

    fun get(file: PsiFile): String? {
        val moduleDir = findModuleDirForFile(file) ?: run {
            println("Module directory could not be found for file: ${file.virtualFile?.path}")
            return null
        }

        val versionFile = moduleDir.toPath().resolve("versions/mainProject")
        if (!Files.exists(versionFile)) {
            println("Main project version file does not exist at: $versionFile")
            return null
        }

        return Files.readString(versionFile).trim()
    }

    fun comparable(file: PsiFile): Int? {
        val version = get(file) ?: return null
        return Versions.makeComparable(version)
    }

}
