package org.polyfrost.intelliprocessor.utils

import com.intellij.codeInsight.completion.*
import com.intellij.lang.LanguageCommenters
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.patterns.ElementPattern
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import java.nio.file.Path

val Editor.activeFile: PsiFile?
    get() = project?.let { project -> PsiDocumentManager.getInstance(project).getPsiFile(this.document) }

val PsiElement.containingComment: PsiComment?
    get() = when (this) {
        is PsiComment -> this
        is PsiWhiteSpace, is PsiPlainText, is LeafPsiElement -> {
            this.prevSibling?.takeIf { it is PsiComment } as? PsiComment
                ?: this.parent?.takeIf { it is PsiComment } as? PsiComment
        }

        else -> parent as? PsiComment
    }

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