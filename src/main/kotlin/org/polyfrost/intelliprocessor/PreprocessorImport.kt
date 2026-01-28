package org.polyfrost.intelliprocessor

import com.intellij.lang.ImportOptimizer
import com.intellij.lang.LanguageImportStatements
import com.intellij.lang.java.JavaImportOptimizer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiImportList
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.impl.source.tree.PsiCommentImpl

class PreprocessorImport : ImportOptimizer {
    override fun supports(file: PsiFile) = file is PsiJavaFile

    override fun processFile(file: PsiFile): Runnable {
        if (file !is PsiJavaFile) return EmptyRunnable.getInstance()
        val imports = file.importList ?: return EmptyRunnable.getInstance()

        if (!hasPreprocessorDirectives(imports)) {
            return LanguageImportStatements.INSTANCE
                .allForLanguage(file.language)
                .first { it is JavaImportOptimizer }
                .processFile(file)
        }

        val optimizedImportList =
            JavaCodeStyleManager
                .getInstance(file.project)
                .prepareOptimizeImportsResult(file)

        return Runnable {
            val manager = PsiDocumentManager.getInstance(file.project)
            val document = manager.getDocument(file)
            if (document != null) manager.commitDocument(document)

            for (import in imports.importStatements)
                if (optimizedImportList.findSingleClassImportStatement(import.qualifiedName) == null) {
                    import.delete()
                }

            if (imports.firstChild is PsiWhiteSpace) imports.firstChild.delete()
        }
    }

    private fun hasPreprocessorDirectives(imports: PsiImportList): Boolean {
        var import = imports.firstChild

        while (import != null) {
            if (import is PsiCommentImpl && import.text.startsWith("//#")) {
                return true
            } else {
                import = import.nextSibling
            }
        }

        return false
    }
}
