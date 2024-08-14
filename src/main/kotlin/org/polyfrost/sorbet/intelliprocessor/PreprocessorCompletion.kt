package org.polyfrost.sorbet.intelliprocessor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns.psiComment
import com.intellij.patterns.StandardPatterns
import com.intellij.util.ProcessingContext

class PreprocessorCompletion : CompletionContributor(), DumbAware {
	init {
		extend(
			CompletionType.BASIC,
			psiComment().withText(
				StandardPatterns.or(
					StandardPatterns.string().startsWith("//"),
					StandardPatterns.string().startsWith("#"),
				),
			),
			PreprocessorCompletionProvider,
		)
	}

	object PreprocessorCompletionProvider : CompletionProvider<CompletionParameters>() {
		override fun addCompletions(
			parameters: CompletionParameters,
			context: ProcessingContext,
			result: CompletionResultSet,
		) {
			for (keyword in KEYWORDS) result.addElement(LookupElementBuilder.create(keyword).bold())
		}

		private val KEYWORDS = listOf("#if", "#else", "#elseif", "#endif", "#ifdef")
	}
}
