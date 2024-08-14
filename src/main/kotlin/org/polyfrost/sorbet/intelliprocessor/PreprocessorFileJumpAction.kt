package org.polyfrost.sorbet.intelliprocessor

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.relativeToOrNull

class PreprocessorFileJumpAction : DumbAwareAction() {
	private fun getMainProjectVersion(rootDirectory: Path): String? {
		val mainProject = rootDirectory.resolve("versions/mainProject")
		if (!mainProject.isRegularFile()) return null
		return mainProject.readText().trim()
	}

	fun showWarning(
		text: String,
		project: Project?,
	) {
		NotificationGroupManager.getInstance()
			.getNotificationGroup("Jump Failure")
			.createNotification(text, NotificationType.ERROR)
			.notify(project)
	}

	override fun actionPerformed(e: AnActionEvent) {
		val project = e.project ?: return showWarning("Missing project", null)

		// TODO: derive this from the source root
		val rootDirectory =
			project.guessProjectDir()?.toNioPath()
				?: return showWarning("Could not find project root directory", project)

		val mainVersion =
			getMainProjectVersion(rootDirectory)
				?: return showWarning("Could not find mainProject. Is this a preprocessor project?", project)
		val editor =
			e.getData(PlatformDataKeys.EDITOR)
				?: return showWarning("Could not find an open editor", project)

		val currentPsiFile =
			getActiveFile(editor, project)
				?: return showWarning("Could not find an opened file", project)
		val currentlyEditingFile =
			currentPsiFile.virtualFile?.toNioPath()
				?: return showWarning("Could not find file on disk", project)

		if (rootDirectory.fileSystem != currentlyEditingFile.fileSystem) {
			return showWarning("Current file not in project root", project)
		}

		val projectPath =
			currentlyEditingFile.relativeToOrNull(rootDirectory)?.toList()
				?: return showWarning("Current file not in project root", project)
		val currentSourceSetFile =
			getSourceSetFrom(projectPath)
				?: return showWarning("File does not seem to be a preprocessor source or generated file", project)

		val allVersions = getAllVersions(rootDirectory)
		if (allVersions.size < 2) {
			return showWarning("Could not find any preprocessed source sets. Make sure to build your project", project)
		}

		val targets =
			allVersions.map { currentSourceSetFile.copy(version = it) }
				.filter { it.version != mainVersion } // The preprocessed sources are not generated for the main project

		val ideView =
			LangDataKeys.IDE_VIEW.getData(e.dataContext)
				?: return showWarning("Could not find IDE view", project)

		val caret = editor.caretModel.currentCaret.visualPosition

		JBPopupFactory.getInstance()
			.createListPopup(
				object : BaseListPopupStep<SourceSetFile>("Choose an Alternative Source File", targets) {
					override fun getTextFor(value: SourceSetFile) = (value.version ?: mainVersion) + "/" + value.classPath.toString()

					override fun isSpeedSearchEnabled() = true

					override fun isSelectable(value: SourceSetFile) = value.version != currentSourceSetFile.version

					override fun getIndexedString(value: SourceSetFile) = value.version ?: mainVersion

					override fun onChosen(
						selectedValue: SourceSetFile?,
						finalChoice: Boolean,
					): PopupStep<*>? {
						selectedValue ?: return null
						val virtualFile = VfsUtil.findFile(rootDirectory.resolve(selectedValue.toRelativePath()), true)
						if (virtualFile == null) {
							showWarning("Could not find file for version ${selectedValue.version ?: mainVersion} on disk. Try building your project", project)
							return null
						}
						val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
						if (psiFile == null) {
							showWarning("Could not open file. Is this project properly loaded?", project)
							return null
						}
						return doFinalStep {
							ideView.selectElement(psiFile)
							val newEditor = FileEditorManager.getInstance(project).getSelectedEditor(virtualFile)
							if (newEditor is TextEditor) {
								newEditor.editor.caretModel.moveToVisualPosition(caret)
							} else {
								showWarning("Could not set cursor for non-text file", project)
							}
						}
					}
				},
			)
			.showCenteredInCurrentWindow(project)
	}

	private fun getActiveFile(
		editor: Editor,
		project: Project,
	): PsiFile? = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)

	// TODO: derive this from the source set roots
	private fun getAllVersions(rootDirectory: Path): MutableList<String?> {
		val versions: MutableList<String?> = mutableListOf(null)
		rootDirectory.resolve("versions")
			.listDirectoryEntries()
			.asSequence()
			.filter { it.isDirectory() }
			.forEach { versions.add(it.fileName.toString()) }
		return versions
	}

	data class SourceSetFile(
		val sourceSetName: String,
		val language: String,
		val classPath: Path,
		val version: String?,
	) {
		fun toRelativePath(): Path =
			if (version == null) {
				Path.of("src", sourceSetName, language).resolve(classPath)
			} else {
				Path.of("versions", version, "build", "preprocessed", sourceSetName, language).resolve(classPath)
			}
	}

	private fun Iterable<Path>.joinToPath() = reduce { acc, path -> acc.resolve(path) }

	private fun getSourceSetFrom(path: List<Path>): SourceSetFile? {
		if (path.size < 4) return null

		// A path in the format of src/<sourceset>/<language>/<package>/<class>
		if (path[0].toString() == "src") {
			return SourceSetFile(
				path[1].toString(),
				path[2].toString(),
				path.subList(3, path.size).joinToPath(),
				null,
			)
		}

		if (path.size < 7) return null

		// A path in the format of `versions/<version>/build/preprocessed/<sourceset>/<language>/<package>/<class>`
		if (path[0].toString() == "versions" &&
			path[2].toString() == "build" &&
			path[3].toString() == "preprocessed"
		) {
			return SourceSetFile(
				path[4].toString(),
				path[5].toString(),
				path.subList(6, path.size).joinToPath(),
				path[1].toString(),
			)
		}

		return null
	}
}
