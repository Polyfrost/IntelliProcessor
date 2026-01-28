package org.polyfrost.intelliprocessor.editor

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.lang.Commenter
import com.intellij.lang.LanguageCommenters
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.impl.source.tree.PsiCommentImpl
import com.intellij.ui.JBColor
import org.polyfrost.intelliprocessor.ALLOWED_FILE_TYPES
import org.polyfrost.intelliprocessor.Scope
import org.polyfrost.intelliprocessor.config.PluginSettings
import org.polyfrost.intelliprocessor.utils.PreprocessorVersion.Companion.toPreprocessorIntOrNull
import java.awt.Color
import java.util.*

val SCHEME = EditorColorsManager.getInstance().globalScheme

val COMMENT_COLOR = SCHEME.getAttributes(DefaultLanguageHighlighterColors.LINE_COMMENT).foregroundColor

fun TextAttributes.fade(): TextAttributes {
    this.foregroundColor = this.foregroundColor?.let { c ->
        // fade halfway to comment colour
        val r = c.red + (COMMENT_COLOR.red - c.red) / 2
        val g = c.green + (COMMENT_COLOR.green - c.green) / 2
        val b = c.blue + (COMMENT_COLOR.blue - c.blue) / 2
        Color(r, g, b)
    }
    return this
}

fun fadedItalic(color: Color? = null): TextAttributes {
    val attributes = TextAttributes.merge(SCHEME.getAttributes(DIRECTIVE_COLOR), ITALIC_ATTRIBUTE)
    if (color != null) attributes.foregroundColor = color
    return attributes.fade()
}

val BOLD_ATTRIBUTE = TextAttributes(null, null, null, null, java.awt.Font.BOLD)
val ITALIC_ATTRIBUTE = TextAttributes(null, null, null, null, java.awt.Font.ITALIC)

val DIRECTIVE_COLOR: TextAttributesKey = DefaultLanguageHighlighterColors.KEYWORD
val DIRECTIVE_ATTRIBUTES_NESTED = listOf(null, JBColor.YELLOW, JBColor.GREEN, JBColor.CYAN, JBColor.BLUE, JBColor.MAGENTA)
    .map { fadedItalic(it) }
val DIRECTIVE_TYPE = HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, DIRECTIVE_COLOR)
val IDENTIFIER_COLOR: TextAttributesKey = DefaultLanguageHighlighterColors.IDENTIFIER
val IDENTIFIER_ATTRIBUTES: TextAttributes = TextAttributes.merge(SCHEME.getAttributes(IDENTIFIER_COLOR), BOLD_ATTRIBUTE).fade()
val IDENTIFIER_TYPE = HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, IDENTIFIER_COLOR)
val NUMBER_COLOR: TextAttributesKey = DefaultLanguageHighlighterColors.NUMBER
val NUMBER_ATTRIBUTES: TextAttributes = TextAttributes.merge(SCHEME.getAttributes(NUMBER_COLOR), BOLD_ATTRIBUTE).fade()
val NUMBER_TYPE = HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, NUMBER_COLOR)

private val WHITESPACES_PATTERN = "\\s+".toRegex()
private val EXPR_PATTERN = "(.+)(==|!=|<=|>=|<|>)(.+)".toRegex()
private val IDENTIFIER_PATTERN = "!?[A-Za-z0-9-]+".toRegex()
private val OR_PATTERN = Regex.escape("||")
private val AND_PATTERN = Regex.escape("&&")
private val SPLIT_PATTERN = "$OR_PATTERN|$AND_PATTERN".toRegex()

class PreprocessorSyntaxHighlight(private val project: Project) : HighlightVisitor, DumbAware {

    private lateinit var holder: HighlightInfoHolder
    private lateinit var commenter: Commenter
    private lateinit var highlighter: SyntaxHighlighter
    private var stack = ArrayDeque<Scope>()
    private var indentStack = ArrayDeque<Int>()
    private var indentGetter: (PsiElement) -> Int = { 0 }

    private val doNestedIfIdentWarn get() = PluginSettings.instance.inspectionHighlightNonIndentedNestedIfs
    private val doIdentMatchWarn get() = PluginSettings.instance.inspectionHighlightCommentsNotMatchingIfIndents
    private val colorNestedIndents get() = PluginSettings.instance.colorNestedPreprocessorComments
    private val colorNestedIndentsOnlyIfInLine get() = PluginSettings.instance.colorNestedPreprocessorCommentsOnlyOnSameIndent
    private val doSpacingWarn get() = PluginSettings.instance.inspectionRequireJavaKotlinSpacingInConditions

    override fun suitableForFile(file: PsiFile): Boolean {
        return file.fileType.name.uppercase(Locale.ROOT) in ALLOWED_FILE_TYPES
    }

    override fun clone(): PreprocessorSyntaxHighlight {
        return PreprocessorSyntaxHighlight(project)
    }

    override fun analyze(
        file: PsiFile,
        updateWholeFile: Boolean,
        holder: HighlightInfoHolder,
        action: Runnable,
    ): Boolean {
        this.holder = holder
        this.commenter = LanguageCommenters.INSTANCE.forLanguage(file.language)
        this.highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(file.language, file.project, file.virtualFile)
        this.stack = ArrayDeque()
        this.indentStack = ArrayDeque()
        indentGetter = {
            val offset = it.textOffset
            val line = file.fileDocument.getLineNumber(offset)
            val lineStart = file.fileDocument.getLineStartOffset(line)
            offset - lineStart
        }
        file.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                visit(element)
                super.visitElement(element)
            }
        })

        return true
    }

    fun nonPreprocessorElement(element: PsiElement) {
        if (indentStack.isNotEmpty()) {
            val indent = indentGetter(element)
            if (indentStack.peekFirst() > indent) {
                fail(element, "Content of preprocessor block is not indented the same or more than the containing preprocessor comment, this may not get processed correctly!")
            }
        }
    }

    override fun visit(element: PsiElement) {
        if (element !is PsiCommentImpl) {
            nonPreprocessorElement(element)
            return
        }

        val source = element.text
        if (!source.startsWith(commenter.lineCommentPrefix ?: return)) {
            return
        }

        val prefixLength = commenter.lineCommentPrefix!!.length
        val comment = source.substring(prefixLength)

        when {
            comment.startsWith("#") -> handleDirectiveComment(element, comment, prefixLength)
            comment.startsWith("$$") -> {
                holder.add("$$".toDirectiveHighlight(element, prefixLength))
                highlightCodeBlock(element, element.startOffset + prefixLength + 2, comment.drop(2))

                if (doIdentMatchWarn) {
                    val indent = indentGetter(element)
                    val previousIndent = indentStack.peekFirst()
                    if (indent != (previousIndent ?: -1)) {
                        warn(element, "\"$$\" line is not indented the same as it's containing block (Code clarity)")
                    }
                }
            }
        }
    }

    private fun handleDirectiveComment(element: PsiCommentImpl, comment: String, prefixLength: Int) {
        val segments = comment.drop(1).split(WHITESPACES_PATTERN, limit = 2)
        when (val directive = segments[0]) {
            "if", "elseif" -> handleIfOrElseIf(element, directive, segments, prefixLength)
            "ifdef" -> handleIfDef(element, segments, prefixLength)
            "else" -> handleElse(element, segments, prefixLength)
            "endif" -> handleEndIf(element, segments, prefixLength)
            else -> fail(element, "Unknown preprocessor directive \"$directive\"")
        }
    }

    private fun handleIfOrElseIf(element: PsiCommentImpl, directive: String, segments: List<String>, prefixLength: Int) {
        if (directive == "elseif") {
            val previous = stack.peekFirst()
            if (previous != Scope.IF) {
                fail(element, "\"elseif\" must follow \"if\" or \"elseif\" (last in scope: ${previous?.name})")
                return
            }

            stack.pop()
        }

        val indent = indentGetter(element)
        val previousIndent = indentStack.peekFirst()
        if (directive == "if") {
            if (doNestedIfIdentWarn && indent <= (previousIndent ?: -1)) {
                warn(element, "\"$directive\" is not indented more than it's outer \"if\" block (Code clarity)")
            }
            indentStack.push(indent)
        } else if (directive == "elseif") {
            if (doIdentMatchWarn && indent != (previousIndent ?: -1)) {
                warn(element, "\"$directive\" is not indented the same as it's starting \"if\" (Code clarity)")
            }
        }

        stack.push(Scope.IF)
        holder.add(directive.toDirectiveHighlight(element, prefixLength))

        if (segments.size < 2) {
            fail(element, "\"$directive\" requires a condition", eol = true)
            return
        }

        val conditionSource = segments[1]
        var offset = prefixLength + directive.length + 2
        for (expr in conditionSource.split(SPLIT_PATTERN)) {
            val trimmed = expr.trim()
            val position = element.text.indexOf(trimmed, offset).takeIf { it >= 0 } ?: continue
            offset = position + trimmed.length

            EXPR_PATTERN.matchEntire(trimmed)?.let { m ->
                m.groups[1]?.toHighlight(element, position)?.let(holder::add)
                if (doSpacingWarn && !trimmed.contains(" ${m.groups[2]?.value} "))
                    warn(element, "Operator \"${m.groups[2]?.value}\" should be surrounded by spaces for clarity & consistency with Java / Kotlin styling.")
                m.groups[3]?.toHighlight(element, position)?.let(holder::add)
            } ?: run {
                IDENTIFIER_PATTERN.matchEntire(trimmed)?.let { idMatch ->
                    idMatch.groups[0]?.toHighlight(element, position)?.let(holder::add)
                } ?: holder.add(trimmed.toInvalidConditionErrorHighlight(element, position))
            }
        }
    }

    private fun handleIfDef(element: PsiCommentImpl, segments: List<String>, prefixLength: Int) {

        val indent = indentGetter(element)
        if (doNestedIfIdentWarn) {
            val previousIndent = indentStack.peekFirst()
            if (indent <= (previousIndent ?: -1)) {
                warn(element, "\"ifdef\" is not indented more than it's outer \"if\" block (Code clarity)")
            }
        }
        indentStack.push(indent)

        stack.push(Scope.IF)
        holder.add("ifdef".toDirectiveHighlight(element, prefixLength))

        if (segments.size < 2) {
            fail(element, "\"ifdef\" requires an identifier", eol = true)
            return
        }

        val id = segments[1]
        val start = element.startOffset + prefixLength + "ifdef".length + 2
        holder.add(
            HighlightInfo.newHighlightInfo(IDENTIFIER_TYPE)
                .range(start, start + id.length)
                .textAttributes(IDENTIFIER_ATTRIBUTES)
                .create()
        )
    }

    private fun handleElse(element: PsiCommentImpl, segments: List<String>, prefixLength: Int) {
        val previous = stack.peekFirst()
        if (previous != Scope.IF) {
            fail(element, "\"else\" must follow \"if\" (last in scope: ${previous?.name})")
            return
        }
        if (doIdentMatchWarn) {
            val indent = indentGetter(element)
            val previousIndent = indentStack.peekFirst()
            if (indent != (previousIndent ?: -1)) {
                warn(element, "\"else\" is not indented the same as it's starting \"if\" (Code clarity)")
            }
        }

        stack.pop()
        stack.push(Scope.ELSE)
        holder.add("else".toDirectiveHighlight(element, prefixLength))

        if (segments.size > 1) {
            fail(element, "\"else\" should not have arguments")
        }
    }

    private fun handleEndIf(element: PsiCommentImpl, segments: List<String>, prefixLength: Int) {
        val previous = stack.peekFirst()
        if (previous != Scope.IF && previous != Scope.ELSE) {
            fail(element, "\"endif\" must follow \"if\" or \"else\" (last in scope: ${previous?.name})")
            return
        }
        if (doIdentMatchWarn) {
            val indent = indentGetter(element)
            val previousIndent = indentStack.peekFirst()
            if (indent != (previousIndent ?: -1)) {
                warn(element, "\"endif\" is not indented the same as it's starting \"if\" (Code clarity)")
            }
        }

        stack.pop()
        holder.add("endif".toDirectiveHighlight(element, prefixLength))
        indentStack.pop()

        if (segments.size > 1) {
            fail(element, "\"endif\" should not have arguments")
        }
    }

    private fun highlightCodeBlock(element: PsiCommentImpl, startOffset: Int, text: String) {
        val lexer = highlighter.highlightingLexer
        lexer.start(text)

        while (lexer.tokenType != null) {
            val mergedAttr = highlighter.getTokenHighlights(lexer.tokenType!!)
                .fold(TextAttributes()) { acc, attr ->
                    TextAttributes.merge(acc, SCHEME.getAttributes(attr))
                }.fade()

            holder.add(
                HighlightInfo.newHighlightInfo(HighlightInfoType.INJECTED_LANGUAGE_FRAGMENT)
                    .range(
                        element,
                        startOffset + lexer.tokenStart,
                        startOffset + lexer.tokenEnd
                    )
                    .textAttributes(mergedAttr)
                    .create()
            )

            lexer.advance()
        }
    }

    private fun fail(element: PsiElement, message: String, eol: Boolean = false) =
        highlightType(element, message, eol, HighlightInfoType.ERROR)

    private fun warn(element: PsiElement, message: String, eol: Boolean = false) =
        highlightType(element, message, eol, HighlightInfoType.WEAK_WARNING)

    private fun highlightType(element: PsiElement, message: String, eol: Boolean = false, type: HighlightInfoType) {
        for (i in 0..holder.size() - 1) {
            val info = holder.get(i)
            if (info.startOffset == element.textRange.startOffset
                && info.description == message
                && info.type == type
            ) {
                return // Avoid repeating the same highlight for each element
            }
        }

        val builder = HighlightInfo.newHighlightInfo(type)
            .descriptionAndTooltip(message)

        if (eol) {
            builder.endOfLine().range(element.textRange.endOffset, element.textRange.endOffset)
        } else {
            builder.range(element)
        }

        holder.add(builder.create())
    }

    private fun MatchGroup.toHighlight(element: PsiCommentImpl, offset: Int): HighlightInfo? {
        val trimmed = value.trim()
        val type = if (trimmed.toPreprocessorIntOrNull() != null) NUMBER_TYPE else IDENTIFIER_TYPE
        val attr = if (trimmed.toPreprocessorIntOrNull() != null) NUMBER_ATTRIBUTES else IDENTIFIER_ATTRIBUTES
        return HighlightInfo.newHighlightInfo(type)
            .textAttributes(attr)
            .range(
                element.startOffset + offset + range.first,
                element.startOffset + offset + range.last + 1
            )
            .create()
    }

    private fun String.toDirectiveHighlight(element: PsiCommentImpl, offset: Int): HighlightInfo? =
        HighlightInfo.newHighlightInfo(DIRECTIVE_TYPE)
            .textAttributes(DIRECTIVE_ATTRIBUTES_NESTED.let {
                if (colorNestedIndents) {
                    val amount = if (colorNestedIndentsOnlyIfInLine) {
                        indentStack.count { i -> i == indentGetter(element)} - 1
                    } else {
                        indentStack.size - 1
                    }.coerceAtLeast(0)
                    it[amount % it.size]
                } else it[0]
            })
            .range(element.startOffset + offset, element.startOffset + offset + 1 + length)
            .create()

    private fun String.toInvalidConditionErrorHighlight(element: PsiCommentImpl, offset: Int): HighlightInfo? =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .descriptionAndTooltip("Invalid condition \"$this\"")
            .range(element.startOffset + offset, element.startOffset + offset + length)
            .create()

}
