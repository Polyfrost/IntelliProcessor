package org.polyfrost.intelliprocessor.utils

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.util.startOffset
import org.polyfrost.intelliprocessor.utils.PreprocessorVersion.Companion.toPreprocessorIntOrNull


class PreprocessorConditions private constructor(
    private val trueConditions: List<PreprocessorDirective>,
    private val falseConditions: List<PreprocessorDirective>
) {
    // Unknown / Unresolvable expressions will evaluate as null, and return failureResult
    fun testVersion(version: PreprocessorVersion, failureResult: Boolean = true): Boolean {
        // Check all conditions that should be true
        for (directive in trueConditions) {
            when (directive) {
                is PreprocessorDirective.If, is PreprocessorDirective.ElseIf -> {
                    val condition = (directive as ConditionContainingDirective).condition
                    if (!(evaluateBooleanConditions(condition, version) ?: return failureResult)) {
                        return false
                    }
                }
                is PreprocessorDirective.IfDef -> continue // TODO
                is PreprocessorDirective.Else -> continue
                is PreprocessorDirective.EndIf -> break
            }
        }

        // Then check all conditions that should be false
        for (directive in falseConditions) {
            when (directive) {
                is PreprocessorDirective.If, is PreprocessorDirective.ElseIf -> {
                    val condition = (directive as ConditionContainingDirective).condition
                    if (evaluateBooleanConditions(condition, version) ?: return failureResult) {
                        return false
                    }
                }
                is PreprocessorDirective.IfDef -> return false // TODO
                is PreprocessorDirective.Else -> return false // Shouldn't ever occur
                is PreprocessorDirective.EndIf -> break
            }
        }

        return true
    }

    companion object {

        // Only providing file and an offset point within it, we find the nearest preceding directive comment and use that as the starting point
        fun findEnclosingConditionsOrNull(offset: Int, file: PsiFile): PreprocessorConditions? {
            val directives = file.allPreprocessorDirectiveComments()
            val previousComment = directives.lastOrNull { it.startOffset <= offset } ?: return null
            return findEnclosingConditionsOrNull(previousComment, directives)
        }

        fun findEnclosingConditionsOrNull(
            comment: PsiComment, // This comment should only be an already identified directive comment
            allDirectives: List<PsiComment>
        ): PreprocessorConditions? {
            var index = allDirectives.indexOfFirst { it === comment }
            if (index == -1) return null

            val trueBlock = mutableListOf<PreprocessorDirective>()
            val falseBlock = mutableListOf<PreprocessorDirective>()
            var sibling: PsiComment? = comment
            var nesting = 0

            // Tracks whether our reverse iteration has passed an else/elseif directive
            // If so, we add further directives to the falseBlock instead of the trueBlock, until we leave the initial if block
            var elseNesting = false

            fun prev(): PsiComment? = allDirectives.getOrNull(--index)

            while (sibling != null) {
                val directive = sibling.parseDirective()
                if (directive == null) {
                    sibling = prev()
                    continue
                }

                when (directive) {
                    is PreprocessorDirective.EndIf -> {
                        nesting++
                    }

                    is PreprocessorDirective.If, is PreprocessorDirective.IfDef -> {
                        if (nesting == 0) {
                            if (elseNesting) {
                                falseBlock.add(0, directive)
                            } else {
                                trueBlock.add(0, directive)
                            }
                            elseNesting = false
                        } else {
                            nesting--
                        }
                    }

                    is PreprocessorDirective.ElseIf, is PreprocessorDirective.Else -> {
                        if (nesting == 0) {
                            if (elseNesting) {
                                falseBlock.add(0, directive)
                            } else {
                                trueBlock.add(0, directive)
                            }
                            elseNesting = true
                        }
                    }
                }
                sibling = prev()
            }

            if (trueBlock.isEmpty() && falseBlock.isEmpty()) return null

            return PreprocessorConditions(trueBlock, falseBlock)
        }

        private fun logAndNull(str: String): Boolean? {
            println(str)
            return null
        }

        private fun evaluateBooleanConditions(conditions: String, currentVersion: PreprocessorVersion): Boolean? {
            // Multiple conditions separated by && or ||
            if (conditions.contains("||") || conditions.contains("&&")) {
                return evaluateOrConditions(conditions, currentVersion)
            }

            // Single condition
            return evaluateCondition(conditions, currentVersion)
        }

        private fun evaluateOrConditions(conditionRaw: String, currentVersion: PreprocessorVersion): Boolean? {
            val conditions = conditionRaw.split("||")
            if (conditions.size == 1) return evaluateAndConditions(conditionRaw, currentVersion)

            var i = 0
            while (i < conditions.size) {
                val or = evaluateAndConditions(conditions[i], currentVersion)
                    ?: return logAndNull("Could not evaluate OR sub condition: ${conditions[i]}")
                if (or) return true
                i++
            }
            return false
        }

        private fun evaluateAndConditions(conditionRaw: String, currentVersion: PreprocessorVersion): Boolean? {
            val conditions = conditionRaw.split("&&")
            if (conditions.size == 1) return evaluateCondition(conditionRaw, currentVersion)

            var i = 0
            while (i < conditions.size) {
                val and = evaluateCondition(conditions[i], currentVersion)
                    ?: return logAndNull("Could not evaluate AND sub condition: ${conditions[i]}")
                if (!and) return false
                i++
            }
            return true
        }

        private fun evaluateCondition(conditionRaw: String, currentVersion: PreprocessorVersion): Boolean? {
            val condition = conditionRaw.trim()
            if (!condition.startsWith("MC")) {

                // Check simple loader conditions
                val lower = condition.lowercase().removePrefix("!")
                if (lower.contains("fabric") || lower.contains("forge")) {
                    return condition.startsWith("!") != (currentVersion.loader == lower)
                }

                return logAndNull("Could not evaluate unknown condition: $condition") // Unknown conditions are always considered "active"
            }

            val match = Regex("""MC\s*(==|!=|<=|>=|<|>)\s*(\S+)""").find(condition)
                ?: return logAndNull("Could not evaluate (MC ?? Number) condition: $condition")
            val (operator, rhsStr) = match.destructured


            val rhs = rhsStr.toPreprocessorIntOrNull()
                ?: return logAndNull("Could not evaluate version number in MC condition: $condition")

            val compare = currentVersion.mc
            return when (operator) {
                "==" -> compare == rhs
                "!=" -> compare != rhs
                "<=" -> compare <= rhs
                ">=" -> compare >= rhs
                "<" -> compare < rhs
                ">" -> compare > rhs
                else -> logAndNull("Could not evaluate MC condition operator: $condition")
            }
        }
    }
}