package org.polyfrost.intelliprocessor.editor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns.psiComment
import com.intellij.psi.PsiComment
import org.polyfrost.intelliprocessor.KEYWORDS
import org.polyfrost.intelliprocessor.utils.register

class PreprocessorKeywordCompletion : CompletionContributor(), DumbAware {

    companion object {
        private const val PREFIX = "//#"
    }

    init {
        register(psiComment()) { parameters, context, result ->
            val position = parameters.position
            val comment = position as? PsiComment ?: return@register
            val text = comment.text
            if (!text.startsWith(PREFIX)) {
                return@register
            }

            val caretOffset = parameters.offset - comment.textRange.startOffset
            if (caretOffset < PREFIX.length) {
                // If the caret is at the start of the comment, suggest keywords
                suggestAllKeywords(result)
                return@register
            }

            val directiveText = text.substring(PREFIX.length)
            val caretRelative = caretOffset - PREFIX.length

            // Get the index of the first non-word character (e.g., space, symbol, etc.)
            val firstSeparator = directiveText.indexOfFirst { !Character.isLetterOrDigit(it) }
            val endOfKeyword = if (firstSeparator == -1) directiveText.length else firstSeparator

            if (caretRelative > endOfKeyword) {
                // Caret is outside of keyword part
                return@register
            }

            val prefix = directiveText.substring(0, caretRelative)
            val matches = KEYWORDS
                .map { it.removePrefix("#") }
                .filter { it.startsWith(prefix, ignoreCase = true) }

            for (keyword in matches) {
                result.addElement(LookupElementBuilder.create(keyword).bold())
            }
        }
    }

    private fun suggestAllKeywords(result: CompletionResultSet) {
        for (keyword in KEYWORDS.map { keyword ->
            keyword.removePrefix("#") // We want to suggest keywords without the leading '#'
        }) {
            result.addElement(LookupElementBuilder.create(keyword).bold())
        }
    }

}
