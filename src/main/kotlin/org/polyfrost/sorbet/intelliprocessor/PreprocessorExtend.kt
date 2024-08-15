package org.polyfrost.sorbet.intelliprocessor

import com.intellij.codeInsight.editorActions.EnterHandler
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.PsiCommentImpl

class PreprocessorExtend : EnterHandlerDelegateAdapter(), DumbAware {
	override fun preprocessEnter(
		file: PsiFile,
		editor: Editor,
		caretOffset: Ref<Int>,
		caretAdvance: Ref<Int>,
		dataContext: DataContext,
		originalHandler: EditorActionHandler?,
	): Result {
		if (EnterHandler.getLanguage(dataContext)?.associatedFileType?.name?.uppercase(java.util.Locale.getDefault()) !in ALLOWED_TYPES) {
			return Result.Continue
		}

		val caret: Int = caretOffset.get().toInt()
		val psiAtOffset = file.findElementAt(caret)

		if (psiAtOffset is PsiCommentImpl) {
			if (!psiAtOffset.text.startsWith("//$$")) return Result.Continue
			val posInText = caret - psiAtOffset.startOffset
			if (posInText < 4) return Result.DefaultForceIndent

			editor.document.insertString(editor.caretModel.offset, "//$$ ")
			caretAdvance.set(5)
			return Result.DefaultForceIndent
		} else if (psiAtOffset?.prevSibling is PsiCommentImpl) {
			if (!psiAtOffset.prevSibling.text.startsWith("//$$")) return Result.Continue
			val posInText = caret - psiAtOffset.prevSibling.textRange.startOffset
			if (posInText < 4) return Result.DefaultForceIndent

			editor.document.insertString(editor.caretModel.offset, "//$$ ")
			caretAdvance.set(5)
			return Result.DefaultForceIndent
		}

		return Result.Continue
	}
}
