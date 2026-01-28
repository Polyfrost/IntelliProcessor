package org.polyfrost.intelliprocessor.config

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@State(name = "IntelliProcessor", storages = [Storage("IntelliProcessor.xml")])
@Service
class PluginSettings : PersistentStateComponent<PluginSettings> {
    var foldAllBlocksByDefault: Boolean = false
    var foldInactiveBlocksByDefault: Boolean = true
    var inspectionHighlightNonIndentedNestedIfs: Boolean = false
    var inspectionHighlightCommentsNotMatchingIfIndents: Boolean = true
    var hideUnmatchedVersions: Boolean = false
    var addPreprocessorCommentOnEnter = true
    var colorNestedPreprocessorComments = true
    var colorNestedPreprocessorCommentsOnlyOnSameIndent = false
    var inspectionHighlightContentNotMatchingIfIndents = true
    var inspectionRequireJavaKotlinSpacingInConditions = false

    override fun getState(): PluginSettings = this

    override fun loadState(state: PluginSettings) {
        this.foldAllBlocksByDefault = state.foldAllBlocksByDefault
        this.foldInactiveBlocksByDefault = state.foldInactiveBlocksByDefault
        this.inspectionHighlightNonIndentedNestedIfs = state.inspectionHighlightNonIndentedNestedIfs
        this.inspectionHighlightCommentsNotMatchingIfIndents = state.inspectionHighlightCommentsNotMatchingIfIndents
        this.hideUnmatchedVersions = state.hideUnmatchedVersions
        this.addPreprocessorCommentOnEnter = state.addPreprocessorCommentOnEnter
        this.colorNestedPreprocessorComments = state.colorNestedPreprocessorComments
        this.colorNestedPreprocessorCommentsOnlyOnSameIndent = state.colorNestedPreprocessorCommentsOnlyOnSameIndent
        this.inspectionHighlightContentNotMatchingIfIndents = state.inspectionHighlightContentNotMatchingIfIndents
        this.inspectionRequireJavaKotlinSpacingInConditions = state.inspectionRequireJavaKotlinSpacingInConditions
    }

    companion object {
        val instance: PluginSettings
            get() = service()

        // Helper to modify settings and correctly persist changes
        fun modify(action: PluginSettings.() -> Unit) {
            val settings = PluginSettings().also { it.loadState(instance) }
            settings.action()
            instance.loadState(settings) // Pass changes back to instance via loadState() to persist
        }
    }
}