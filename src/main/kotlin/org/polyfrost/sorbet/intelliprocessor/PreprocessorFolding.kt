package org.polyfrost.sorbet.intelliprocessor

import com.intellij.lang.ASTNode
import com.intellij.lang.LanguageCommenters
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil

class PreprocessorFolding : FoldingBuilderEx(), DumbAware {
	override fun getPlaceholderText(node: ASTNode): String {
		if (node !is PsiComment) return "...11".also { println("Not a comment? Is $node") }
		val directivePrefix =
			LanguageCommenters.INSTANCE.forLanguage(node.language).lineCommentPrefix
				?: return "...222".also { println("Null comment prefix?") }
		return (node as ASTNode).text.substring(directivePrefix.length)
	}

	override fun buildFoldRegions(
		root: PsiElement,
		document: Document,
		quick: Boolean,
	): Array<FoldingDescriptor> {
		val descriptors = mutableListOf<FoldingDescriptor>()
		val directivePrefix = (LanguageCommenters.INSTANCE.forLanguage(root.language).lineCommentPrefix ?: return emptyArray()) + "#"
		val allDirectives = PsiTreeUtil.findChildrenOfType(root, PsiComment::class.java).filter { it.text.startsWith(directivePrefix) }

		for ((index, directive) in allDirectives.withIndex())
			if (directive.text.run {
					startsWith(directivePrefix + "if") ||
						startsWith(directivePrefix + "ifdef") ||
						startsWith(directivePrefix + "else")
				} && index + 1 < allDirectives.size
			) {
				val nextDirective = allDirectives[index + 1]
				val endOffset =
					when {
						nextDirective.text.startsWith(directivePrefix + "endif") -> nextDirective.textRange.endOffset
						nextDirective.prevSibling is PsiWhiteSpace -> nextDirective.prevSibling.textRange.startOffset
						else -> nextDirective.textRange.startOffset
					}

				descriptors.add(FoldingDescriptor(directive, TextRange(directive.textRange.startOffset, endOffset)))
			}

		return descriptors.toTypedArray()
	}

	override fun isCollapsedByDefault(node: ASTNode) = false
}
