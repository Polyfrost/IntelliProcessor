package dev.deftu.intelliprocessor.utils

import com.intellij.psi.PsiComment
import dev.deftu.intelliprocessor.utils.PreprocessorDirective.Else
import dev.deftu.intelliprocessor.utils.PreprocessorDirective.EndIf

interface ConditionContainingDirective {
    val condition: String
}

sealed interface PreprocessorDirective {

    data class If(override val condition: String) : PreprocessorDirective, ConditionContainingDirective
    data class IfDef(val variable: String) : PreprocessorDirective
    data class ElseIf(override val condition: String) : PreprocessorDirective, ConditionContainingDirective
    data object Else : PreprocessorDirective
    data object EndIf : PreprocessorDirective

}

fun PsiComment.parseDirective(): PreprocessorDirective? {
    fun String.clean(name: String): String? {
        return this.removePrefix(name).trim().takeIf(String::isNotBlank)
    }

    val text = text.trim().removePrefix("//#").trim()
    return when {
        text.startsWith("if") -> text.clean("if")?.let(PreprocessorDirective::If)
        text.startsWith("ifdef") -> text.clean("ifdef")?.let(PreprocessorDirective::IfDef)
        text.startsWith("elseif") -> text.clean("elseif")?.let(PreprocessorDirective::ElseIf)
        text == "else" -> Else
        text == "endif" -> EndIf
        else -> null
    }
}
