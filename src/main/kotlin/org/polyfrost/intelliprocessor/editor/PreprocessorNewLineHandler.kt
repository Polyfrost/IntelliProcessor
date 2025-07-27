package org.polyfrost.intelliprocessor.editor

import com.intellij.codeInsight.editorActions.EnterHandler
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.polyfrost.intelliprocessor.ALLOWED_FILE_TYPES
import org.polyfrost.intelliprocessor.utils.*
import java.util.Locale

class PreprocessorNewLineHandler : EnterHandlerDelegateAdapter(), DumbAware {

    override fun preprocessEnter(
        file: PsiFile,
        editor: Editor,
        caretOffset: Ref<Int>,
        caretAdvance: Ref<Int>,
        dataContext: DataContext,
        originalHandler: EditorActionHandler?
    ): Result {
        val fileTypeName = EnterHandler.getLanguage(dataContext)
            ?.associatedFileType
            ?.name
            ?.uppercase(Locale.ROOT)

        if (fileTypeName !in ALLOWED_FILE_TYPES) {
            return Result.Continue
        }

        val caretPos = caretOffset.get()
        val psiAtOffset = file.findElementAt(caretPos) ?: return Result.Continue
        val comment = psiAtOffset.containingComment ?: return Result.Continue

        val posInText = caretPos - comment.textRange.startOffset
        if (posInText < 4) {
            return Result.DefaultForceIndent
        }

        val conditionals = findEnclosingConditionalBlock(comment)
        val currentVersion = MainProject.comparable(file)
        if (currentVersion != null && isInsideActiveBlock(conditionals, currentVersion)) {
            return Result.Continue
        }

        // Execute the default enter behavior (new line + correct indentation)
        originalHandler?.execute(editor, editor.caretModel.currentCaret, dataContext)

        // Insert "//$$ " at the new caret position
        val insertText = "//$$ "
        val insertOffset = editor.caretModel.offset.takeIf { it >= 0 } ?: return Result.Continue
        editor.document.insertString(insertOffset, insertText)

        // Move caret ahead
        editor.caretModel.moveToOffset(insertOffset + insertText.length)
        caretAdvance.set(insertText.length)

        return Result.Stop
    }

    private fun isInsideActiveBlock(
        conditionals: List<PreprocessorDirective>,
        currentVersion: Int
    ): Boolean {
        for (directive in conditionals) {
            when (directive) {
                is PreprocessorDirective.If, is PreprocessorDirective.ElseIf -> {
                    val condition = (directive as ConditionContainingDirective).condition
                    if (evaluateCondition(condition, currentVersion)) {
                        return true
                    }
                }

                is PreprocessorDirective.IfDef -> return true // TODO
                is PreprocessorDirective.Else -> return true
                is PreprocessorDirective.EndIf -> break
            }
        }
        return false
    }

    private fun evaluateCondition(condition: String, currentVersion: Int): Boolean {
        if (!condition.startsWith("MC")) {
            return false
        }

        val match = Regex("""MC\s*(==|!=|<=|>=|<|>)\s*(\S+)""").find(condition) ?: return false
        val (operator, rhsStr) = match.destructured
        val rhs = Versions.makeComparable(rhsStr) ?: return false

        return when (operator) {
            "==" -> currentVersion == rhs
            "!=" -> currentVersion != rhs
            "<=" -> currentVersion <= rhs
            ">=" -> currentVersion >= rhs
            "<"  -> currentVersion < rhs
            ">"  -> currentVersion > rhs
            else -> false
        }
    }

    private fun findEnclosingConditionalBlock(comment: PsiComment): List<PreprocessorDirective> {
        val block = mutableListOf<PreprocessorDirective>()
        var sibling: PsiElement? = comment
        var nesting = 0

        while (sibling != null) {
            if (sibling is PsiComment) {
                val directive = sibling.parseDirective()
                if (directive == null) {
                    sibling = sibling.prevSibling
                    continue
                }

                when (directive) {
                    is PreprocessorDirective.EndIf -> {
                        nesting++
                    }

                    is PreprocessorDirective.If, is PreprocessorDirective.IfDef -> {
                        if (nesting == 0) {
                            block.add(0, directive)
                            return block
                        } else {
                            nesting--
                        }
                    }

                    is PreprocessorDirective.ElseIf, is PreprocessorDirective.Else -> {
                        if (nesting == 0) {
                            block.add(0, directive)
                        }
                    }
                }
            }

            sibling = sibling.prevSibling
        }

        return emptyList()
    }

}
