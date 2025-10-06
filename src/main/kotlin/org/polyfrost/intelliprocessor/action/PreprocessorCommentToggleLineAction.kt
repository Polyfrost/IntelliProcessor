package org.polyfrost.intelliprocessor.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.polyfrost.intelliprocessor.utils.PreprocessorContainingBlock
import org.polyfrost.intelliprocessor.utils.activeFile
import org.polyfrost.intelliprocessor.utils.allPreprocessorDirectiveComments

class PreprocessorCommentToggleLineAction  : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        val editor: Editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return warning(project, "Could not find an open editor")
        val document = editor.document

        val startLine = editor.caretModel.primaryCaret.selectionStartPosition.line
        val endLine = editor.caretModel.primaryCaret.selectionEndPosition.line

        WriteCommandAction.runWriteCommandAction(project) {
            for (line in startLine..endLine) {
                val lineStart = document.getLineStartOffset(line)
                val lineEnd = document.getLineEndOffset(line)
                val text = document.getText(TextRange(lineStart, lineEnd))

                val toggleCommentsOff = text.contains("//$$")

                // Clean up any existing toggle comments
                document.replaceString(lineStart, lineEnd, text.replaceFirst(REPLACE, ""))

                if (toggleCommentsOff) return@runWriteCommandAction

                // If the line is blank, just insert a blank comment
                if (text.trim().isEmpty()) {
                    document.replaceString(lineStart, lineEnd, "//$$ ")
                    return@runWriteCommandAction
                }

                // Only comment lines that are indented at least as much as the block start
                val indent = countLeadingSpaces(text)
                val spaces = " ".repeat(indent)
                val spacedComment = "$spaces//$$ "
                document.replaceString(lineStart, lineEnd, text.replaceFirst(spaces, spacedComment))
            }
        }
    }

    private fun countLeadingSpaces(s: String): Int {
        var count = 0
        while (count < s.length && s[count] == ' ') count++
        return count
    }

    companion object {
        private val REPLACE = Regex("""//\$\$\s?""")

        private fun warning(project: Project, content: String) {
            org.polyfrost.intelliprocessor.utils.warning(project, "preprocessor_comment_toggle_failure", content)
        }
    }
}