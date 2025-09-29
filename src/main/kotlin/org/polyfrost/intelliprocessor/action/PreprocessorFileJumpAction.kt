package org.polyfrost.intelliprocessor.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.polyfrost.intelliprocessor.utils.*
import org.polyfrost.intelliprocessor.utils.PreprocessorVersion.Companion.preprocessorMainVersion
import java.nio.file.Path
import kotlin.io.path.relativeToOrNull

open class PreprocessorFileJumpAction : DumbAwareAction() {

    private companion object {
        private const val GROUP_ID = "Jump Failure"

        private fun warning(project: Project, content: String) {
            warning(project, GROUP_ID, content)
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(PlatformDataKeys.EDITOR)
            ?: return warning(project, "Could not find an open editor")
        val currentPsiFile = editor.activeFile
            ?: return warning(project, "Could not find an opened file")
        val rootDirectory = findModuleDirForFile(currentPsiFile)
            ?.toPath()
            ?: return warning(project, "Could not find module directory for file")
        val mainVersion = currentPsiFile.preprocessorMainVersion
            ?: return warning(project, "Could not find mainProject. Is this a preprocessor project?")
        val currentlyEditingFile = currentPsiFile.virtualFile?.toNioPath()
            ?: return warning(project, "Could not find file on disk")
        if (rootDirectory.fileSystem != currentlyEditingFile.fileSystem) {
            return warning(project, "Current file not in project root")
        }

        val projectPath = currentlyEditingFile.relativeToOrNull(rootDirectory)?.toList()
            ?: return warning(project, "Current file not in project root")
        val currentSourceSetFile = getSourceSetFrom(projectPath, mainVersion, rootDirectory)
            ?: return warning(project, "File does not seem to be a preprocessor source or generated file")

        val allVersions = identifyVersionDirectories(rootDirectory)
        if (allVersions.size < 2) {
            return warning(project, "Could not find any preprocessed source sets. Make sure to build your project")
        }

        val targets = allVersions.map { currentSourceSetFile.copy(subVersion = it) }
            .filter { it.subVersion != mainVersion } // The preprocessed sources are not generated for the main project
        val ideView = LangDataKeys.IDE_VIEW.getData(e.dataContext)
            ?: return warning(project, "Could not find IDE view")

        val caret = editor.caretModel.currentCaret.visualPosition

        // For if the caret is inside a preprocessor conditional block, test each target version against the conditions
        val foundConditionContext = testTargetsAgainstPreprocessorConditions(currentPsiFile, editor, targets)

        SourceSetFileDialog(project, targets, foundConditionContext) { selected ->
            val virtualFile = VfsUtil.findFile(rootDirectory.resolve(selected.toRelativePath()), true)
            if (virtualFile == null) {
                warning(
                    project,
                    "Could not find file for version ${selected.displayVersion} on disk. Try building your project"
                )

                return@SourceSetFileDialog
            }

            val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            if (psiFile == null) {
                warning(project, "Could not open file. Is this project properly loaded?")
                return@SourceSetFileDialog
            }

            ideView.selectElement(psiFile)
            val newEditor = FileEditorManager.getInstance(project).getSelectedEditor(virtualFile)
            if (newEditor is TextEditor) {
                newEditor.editor.caretModel.moveToVisualPosition(caret)
            } else {
                warning(project, "Could not set cursor for non-text file")
            }
        }.show()
    }

    // If the caret is inside a preprocessor conditional block, test each target version against the conditions there
    private fun testTargetsAgainstPreprocessorConditions(
        file: PsiFile,
        editor: Editor,
        targets: List<SourceSetFile>
    ): Boolean {
        val selectedPos = editor.caretModel.currentCaret.offset
        val conditions = PreprocessorConditions.findEnclosingConditionsOrNull(selectedPos, file)
        if (conditions != null) {
            targets.forEach { it.metOpeningCondition = conditions.testVersion(it.version) }
        }
        return conditions != null
    }

    private fun getSourceSetFrom(path: List<Path>, mainVersion: String, rootDirectory: Path): SourceSetFile? {
        if (path.size < 4) {
            return null
        }

        // The main file path in the format of src/<sourceset>/<language>/<package>/<class>
        if (path[0].toString() == "src") {
            return SourceSetFile(
                path[1].toString(),
                path[2].toString(),
                path.subList(3, path.size).joinToPath(),
                null,
                mainVersion,
                rootDirectory,
            )
        }

        if (path.size < 7) {
            return null
        }

        // A generated preprocessed path in the format of `versions/<version>/build/preprocessed/<sourceset>/<language>/<package>/<class>`
        if (path[0].toString() == "versions" &&
            path[2].toString() == "build" &&
            path[3].toString() == "preprocessed"
        ) {
            return SourceSetFile(
                path[4].toString(),
                path[5].toString(),
                path.subList(6, path.size).joinToPath(),
                path[1].toString(),
                mainVersion,
                rootDirectory,
            )
        }

        // An override file path in the format of `versions/<version>/src/<sourceset>/<language>/<package>/<class>`
        if (path[0].toString() == "versions" &&
            path[2].toString() == "src"
        ) {
            return SourceSetFile(
                path[3].toString(),
                path[4].toString(),
                path.subList(5, path.size).joinToPath(),
                path[1].toString(),
                mainVersion,
                rootDirectory,
            )
        }

        return null
    }

}
