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

class PreprocessorCommentToggleAction  : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        val editor: Editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return warning(project, "Could not find an open editor")
        val document = editor.document
        val file = editor.activeFile ?: return warning(project, "Could not find an opened file")

        val directives = file.allPreprocessorDirectiveComments()

        if (directives.size < 2) return warning(project, "Could not find any preprocessor blocks in this file")

        val block = PreprocessorContainingBlock.getFor(editor.caretModel.offset, directives)
            ?: return warning(project, "Could not find a preprocessor block at the current caret position")


        val startLine = block.directives[block.startIndex].getLineNumber(true) + 1
        val endLine = block.directives[block.endIndex].getLineNumber(true) - 1

        if (startLine > endLine) return warning(project, "No lines to toggle between the selected directives")

        val excludeLines = block.innerBlocks.map {
            IntRange(
                block.directives[it.start].getLineNumber(true),
            block.directives[it.endInclusive].getLineNumber(true)
            )
        }

        val startIndent = countLeadingSpaces(
            document.getText(TextRange(
                document.getLineStartOffset(startLine - 1),
                document.getLineEndOffset(startLine - 1)
            ))
        )

        val alreadyHasComments = document.getText(TextRange(
            document.getLineStartOffset(startLine),
            document.getLineEndOffset(startLine)
        )).trim().startsWith("//$$")

        val spaces = " ".repeat(startIndent)
        val spacedComment = "$spaces//$$ "

        WriteCommandAction.runWriteCommandAction(project) {
            for (line in startLine..endLine) {

                // Don't modify lines that are part of inner nested blocks
                if (excludeLines.any { line in it }) continue

                val lineStart = document.getLineStartOffset(line)
                val lineEnd = document.getLineEndOffset(line)
                val text = document.getText(TextRange(lineStart, lineEnd))

                // Just remove comments if they are already there
                if (alreadyHasComments) {
                    document.replaceString(lineStart, lineEnd,
                        text.replaceFirst(REPLACE, ""))
                    continue
                }

                // If the line is blank, just insert a blank comment
                if (text.trim().isEmpty()) {
                    document.replaceString(lineStart, lineEnd, spacedComment)
                    continue
                }

                // Only comment lines that are indented at least as much as the block start
                val indent = countLeadingSpaces(text)
                if (indent >= startIndent) {
                    document.replaceString(lineStart, lineEnd,
                        text.replaceFirst(spaces, spacedComment))
                }
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