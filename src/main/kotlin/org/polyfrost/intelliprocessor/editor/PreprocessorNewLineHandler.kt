package org.polyfrost.intelliprocessor.editor

import com.intellij.codeInsight.editorActions.EnterHandler
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile
import org.polyfrost.intelliprocessor.ALLOWED_FILE_TYPES
import org.polyfrost.intelliprocessor.utils.*
import org.polyfrost.intelliprocessor.utils.PreprocessorVersion.Companion.preprocessorVersion
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

        val conditions = PreprocessorConditions.findEnclosingConditionsOrNull(comment, file)
        if (conditions == null) {
            return Result.Continue
        }

        val currentVersion = file.preprocessorVersion
        if (currentVersion != null && conditions.testVersion(currentVersion)) {
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

}
