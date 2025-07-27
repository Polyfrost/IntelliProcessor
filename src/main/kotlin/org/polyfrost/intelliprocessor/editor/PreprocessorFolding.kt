package org.polyfrost.intelliprocessor.editor

import com.intellij.lang.ASTNode
import com.intellij.lang.LanguageCommenters
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

class PreprocessorFolding : FoldingBuilderEx(), DumbAware {

    override fun getPlaceholderText(node: ASTNode): String {
        val comment = node.psi as? PsiComment ?: return "..."
        val directivePrefix = LanguageCommenters.INSTANCE.forLanguage(node.psi.language)?.lineCommentPrefix
            ?: return "..."

        return comment.text.removePrefix(directivePrefix).trim()
    }

    override fun buildFoldRegions(
        root: PsiElement,
        document: Document,
        quick: Boolean,
    ): Array<FoldingDescriptor> {
        val descriptors = mutableListOf<FoldingDescriptor>()
        val directivePrefix = (LanguageCommenters.INSTANCE.forLanguage(root.language).lineCommentPrefix ?: return emptyArray()) + "#"

        val allDirectives = PsiTreeUtil.findChildrenOfType(root, PsiComment::class.java)
            .filter { it.text.startsWith(directivePrefix) }

        val stack = ArrayDeque<PsiComment>()

        for (directive in allDirectives) {
            val text = directive.text

            when {
                text.startsWith(directivePrefix + "if") || text.startsWith(directivePrefix + "ifdef") -> {
                    stack.addLast(directive)
                }

                text.startsWith(directivePrefix + "endif") -> {
                    val startDirective = stack.removeLastOrNull()
                    if (startDirective != null) {
                        descriptors.add(
                            FoldingDescriptor(
                                startDirective,
                                TextRange(startDirective.textRange.startOffset, directive.textRange.endOffset)
                            )
                        )
                    }
                }
            }
        }

        return descriptors.toTypedArray()
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean {
        return false
    }

}
