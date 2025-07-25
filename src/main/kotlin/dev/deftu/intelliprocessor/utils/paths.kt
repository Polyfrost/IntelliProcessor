package dev.deftu.intelliprocessor.utils

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.PsiFile
import java.io.File
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Attempts to locate the actual module directory (not just the source root) for a given PsiFile.
 */
fun findModuleDirForFile(file: PsiFile): File? {
    val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return null
    val roots = ModuleRootManager.getInstance(module).contentRoots
    if (roots.isEmpty()) return null

    // Try to find the topmost directory that contains the content root.
    val contentRoot = VfsUtilCore.virtualToIoFile(roots[0])
    var current = contentRoot

    // Look for something like `build.gradle`, `settings.gradle`, or `.idea` to identify module root
    // This is a heuristic to find the module directory, as it may not always be the content root.
    // We traverse up the directory tree until we find a recognizable module marker file.
    while (current.parentFile != null) {
        if (
            File(current, "build.gradle.kts").exists() ||
            File(current, "build.gradle").exists() ||
            File(current, "settings.gradle.kts").exists() ||
            File(current, "settings.gradle").exists() ||
            File(current, ".idea").exists() ||
            File(current, "versions").exists() ||
            File(current, "gradle").exists()
        ) {
            return current
        }

        current = current.parentFile
    }

    // Fallback: assume the directory above the content root is the module dir
    return contentRoot.parentFile
}

/**
 * Identifies potential Minecraft version directories within the specified root directory.
 * It returns a list of version directory names, or null if no versions are found.
 */
fun identifyVersionDirectories(rootDirectory: Path): MutableList<String?> {
    val versions: MutableList<String?> = mutableListOf(null)
    rootDirectory.resolve("versions")
        .listDirectoryEntries()
        .asSequence()
        .filter(::isPotentialVersionDirectory)
        .forEach { versions.add(it.fileName.toString()) }
    return versions
}

/**
 * Evaluates whether a given directory is potentially a Minecraft version directory.
 * This is determined by checking if the directory contains the usual build files
 * and Gradle caches.
 */
private fun isPotentialVersionDirectory(dir: Path): Boolean {
    if (!dir.isDirectory()) {
        return false
    }

    return dir.listDirectoryEntries().any { path ->
        path.name == ".gradle" ||
        path.name == "build"
    }
}
