package org.polyfrost.intelliprocessor.utils

import com.intellij.codeInsight.completion.*
import com.intellij.lang.LanguageCommenters
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.patterns.ElementPattern
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import java.nio.file.Path

val Editor.activeFile: PsiFile?
    get() = project?.let { project -> PsiDocumentManager.getInstance(project).getPsiFile(this.document) }

fun Iterable<Path>.joinToPath(): Path {
    return reduce { acc, path ->
        acc.resolve(path)
    }
}

inline fun CompletionContributor.register(
    pattern: ElementPattern<out PsiElement>,
    crossinline action: (parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) -> Unit
) {
    extend(CompletionType.BASIC, pattern, object : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
            action(parameters, context, result)
        }
    })
}

fun warning(
    project: Project,
    groupId: String,
    content: String
) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup(groupId)
        .createNotification(content, NotificationType.WARNING)
        .notify(project)
}

fun PsiElement.directivePrefix(): String? {
        return (LanguageCommenters.INSTANCE.forLanguage(language).lineCommentPrefix ?: return null) + "#"
    }

fun PsiFile.allPreprocessorDirectiveComments(): List<PsiComment> {
    val directivePrefix = directivePrefix() ?: return emptyList()
    return PsiTreeUtil.findChildrenOfType(this, PsiComment::class.java)
        .filter { it.text.startsWith(directivePrefix) }
}

data class PreprocessorContainingBlock(
    val directives: List<PsiComment>,
    val startIndex: Int,
    val endIndex: Int,
    val innerBlocks: List<IntRange>
) {
    companion object {
        fun getFor(offset: Int, directives: List<PsiComment>): PreprocessorContainingBlock? {

            // First lets check this caret pos is actually between some directives before we get any more complex
            val previous = directives.lastOrNull { it.textRange.endOffset <= offset } ?: return null
            val next = directives.firstOrNull { it.textRange.startOffset > offset } ?: return null

            // Okay now lets find the containing block, this is a bit tricky because of possible nesting
            // We will iterate backwards from the previous directive to find the start of the block
            // and forwards from the next directive to find the end of the block
            // We also need to keep track of any inner blocks we find along the way so they can be safely ignored later

            val innerBlocks = mutableListOf<IntRange>()

            // Iterate backwards to find the previous containing directive
            val startIndex: Int
            if (previous.text.startsWith("//#endif")) {
                // Nested block, we need to find the matching block start for our level
                var depth = 1
                var index = directives.indexOf(previous)
                var lastEnd = index
                var find: Int? = null
                while (index > 0) {
                    index--
                    val text = directives[index].text
                    if (text.startsWith("//#endif")) {
                        depth++
                        if (depth == 1) {
                            lastEnd = index
                        }
                    } else if (text.startsWith("//#if") || text.startsWith("//#ifdef")) {
                        if (depth == 0) {
                            find = index
                            break
                        }
                        if (depth == 1) {
                            innerBlocks.add(index..lastEnd)
                        }
                        depth--
                    } else if (text.startsWith("//#else") || text.startsWith("//#elseif")) {
                        if (depth == 0) {
                            find = index
                            break
                        }
                    }
                }
                startIndex = find ?: return null
            } else {
                // Simple case, just the previous directive of the same level
                startIndex = directives.indexOf(previous)
            }


            // Now find the next directive to determine the end of the block, this basically repeats the above logic
            val endIndex: Int
            if (next.text.startsWith("//#if") || next.text.startsWith("//#ifdef")) {
                // Nested block, we need to find the matching block end for our level
                var depth = 1
                var index = directives.indexOf(next)
                var lastStart = index
                var find: Int? = null
                while (index < directives.size - 1) {
                    index++
                    val text = directives[index].text
                    if (text.startsWith("//#if") || text.startsWith("//#ifdef")) {
                        depth++
                        if (depth == 1) {
                            lastStart = index
                        }
                    } else if (text.startsWith("//#endif")) {
                        if (depth == 0) {
                            find = index
                            break
                        }
                        if (depth == 1) {
                            innerBlocks.add(lastStart..index)
                        }
                        depth--
                    } else if (text.startsWith("//#else") || text.startsWith("//#elseif")) {
                        if (depth == 0) {
                            find = index
                            break
                        }
                    }
                }
                endIndex = find ?: return null
            } else {
                // Simple case, just the next directive of the same level
                endIndex = directives.indexOf(next)
            }

            return PreprocessorContainingBlock(
                directives,
                startIndex,
                endIndex,
                innerBlocks
            )
        }
    }
}