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
import org.polyfrost.intelliprocessor.config.PluginSettings
import org.polyfrost.intelliprocessor.utils.PreprocessorConditions
import org.polyfrost.intelliprocessor.utils.PreprocessorVersion
import org.polyfrost.intelliprocessor.utils.PreprocessorVersion.Companion.preprocessorVersion
import org.polyfrost.intelliprocessor.utils.allPreprocessorDirectiveComments
import org.polyfrost.intelliprocessor.utils.directivePrefix

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

        // Required to allow the "fold inactive blocks by default" feature
        // Disabled for quick mode so we don't run all the condition checks on the fly
        val preprocessorVersion =
            if (quick || !PluginSettings.instance.foldInactiveBlocksByDefault) null
            else root.containingFile.preprocessorVersion

        val descriptors = mutableListOf<FoldingDescriptor>()
        val directivePrefix = root.directivePrefix()
        val allDirectives = root.allPreprocessorDirectiveComments()
        val stack = ArrayDeque<PsiComment>()

        for (directive in allDirectives) {
            val text = directive.text

            when {
                text.startsWith(directivePrefix + "if") || text.startsWith(directivePrefix + "ifdef") -> {
                    stack.addLast(directive)
                }

                text.startsWith(directivePrefix + "else") -> { // elseif caught too
                    val startDirective = stack.removeLastOrNull()
                    if (startDirective != null) {
                        val commentLine = document.getLineNumber(directive.textOffset)
                        if (commentLine > 0) {
                            val prevLineEnd = document.getLineEndOffset(commentLine - 1)
                            descriptors.add(
                                fold(startDirective,
                                    startDirective.textRange.startOffset,
                                    prevLineEnd,
                                    preprocessorVersion,
                                    allDirectives
                                )
                            )
                            stack.addLast(directive)
                        }
                    }
                }

                text.startsWith(directivePrefix + "endif") -> {
                    val startDirective = stack.removeLastOrNull()
                    if (startDirective != null) {
                        descriptors.add(
                            fold(startDirective,
                                startDirective.textRange.startOffset,
                                directive.textRange.endOffset,
                                preprocessorVersion,
                                allDirectives
                            )
                        )
                    }
                }
            }
        }

        return descriptors.toTypedArray()
    }

    private fun fold(element: PsiComment, startOffset: Int, endOffset: Int, thisVersion: PreprocessorVersion?, allDirectives: List<PsiComment>): FoldingDescriptor {
        if (thisVersion == null || PluginSettings.instance.foldAllBlocksByDefault) {
            return FoldingDescriptor(element, TextRange(startOffset, endOffset))
        }

        val shouldFold = PreprocessorConditions.findEnclosingConditionsOrNull(element, allDirectives)?.let{
            !it.testVersion(thisVersion)
        }

        return FoldingDescriptor(
            element.node,
            TextRange(startOffset, endOffset),
            null,
            emptySet(),
            false,
            null,
            shouldFold
        )
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean {
        return PluginSettings.instance.foldAllBlocksByDefault
    }

}
