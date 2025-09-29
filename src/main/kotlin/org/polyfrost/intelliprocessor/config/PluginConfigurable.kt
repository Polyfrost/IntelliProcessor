package org.polyfrost.intelliprocessor.config

import com.intellij.openapi.options.Configurable
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EtchedBorder
import javax.swing.border.TitledBorder

class PluginConfigurable : Configurable {
    private lateinit var panel: JPanel
    private lateinit var foldInactiveBlocksByDefaultCheckbox: JCheckBox
    private lateinit var foldAllBlocksByDefaultCheckbox: JCheckBox
    private lateinit var inspectionHighlightNonIndentedNestedIfsCheckbox: JCheckBox
    private lateinit var inspectionHighlightCommentsNotMatchingIfIndentsCheckbox: JCheckBox
    private lateinit var hideUnmatchedVersionsCheckbox: JCheckBox


    override fun getDisplayName(): String = "IntelliProcessor"

    override fun createComponent(): JComponent {

        // Setup components

        fun <J : JComponent> J.tooltip(str: String): J = apply { toolTipText = str }

        foldInactiveBlocksByDefaultCheckbox = JCheckBox("Fold inactive preprocessor blocks by default")
            .tooltip("Automatically folds preprocessor blocks that are conditionally inactive. (E.G. 'MC>=1.20' blocks in a 1.19 file)")

        foldAllBlocksByDefaultCheckbox = JCheckBox("Fold all preprocessor blocks by default").apply {
            addChangeListener { event ->
                // Disable the "fold inactive blocks" option if "fold all blocks" is enabled
                foldInactiveBlocksByDefaultCheckbox.isEnabled = !(event.source as JCheckBox).isSelected
            }
        }

        inspectionHighlightNonIndentedNestedIfsCheckbox =
            JCheckBox("Highlight non-indented nested \"if\" preprocessor directives (Code clarity)")
                .tooltip(
                    "Highlights nested \"if\" preprocessor directives that are not indented more than their enclosing preprocessor block.\n" +
                        "\nThis does not break preprocessing, but can help improve code clarity by visually indicating the nested structure of preprocessor blocks."
                )

        inspectionHighlightCommentsNotMatchingIfIndentsCheckbox =
            JCheckBox("Highlight preprocessor comments not matching their \"if\"'s indent (Code clarity)")
                .tooltip(
                    "Highlights preprocessor comments whose indent does not match the indent of the corresponding \"if\" directive.\n" +
                        "\nThis does not break preprocessing, but can help improve code clarity by visually linking preprocessor comments to their corresponding \"if\" directives."
                )

        hideUnmatchedVersionsCheckbox = JCheckBox("Hide results that do not meet preprocessor conditions at the caret")
            .tooltip("Hides version results in the 'Jump To Pre-Processed File' dialog that do not match the current file's preprocessor conditions found at the caret position.")


        // Arrange components

        fun titledBlock(str: String, block: JPanel.() -> Unit): JPanel = JPanel().apply {
            border = TitledBorder(EtchedBorder(),str)
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            block()
        }
        
        panel = JPanel()

        panel.apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)

            add(titledBlock("Folding") {
                add(foldAllBlocksByDefaultCheckbox)
                add(foldInactiveBlocksByDefaultCheckbox)
            })
            
            add(titledBlock("Inspection Highlighting") {
                add(inspectionHighlightNonIndentedNestedIfsCheckbox)
                add(inspectionHighlightCommentsNotMatchingIfIndentsCheckbox)
            })

            add(titledBlock("Jump To Pre-Processed File Action") {
                add(hideUnmatchedVersionsCheckbox)
            })

            add(titledBlock("Info") {
                add(JLabel("The keybinds can be configured from: Keymap > Plugins > IntelliProcessor"))
            })
        }

        reset()
        return panel
    }

    override fun isModified(): Boolean =
            foldAllBlocksByDefaultCheckbox.isSelected != PluginSettings.instance.foldAllBlocksByDefault
                || foldInactiveBlocksByDefaultCheckbox.isSelected != PluginSettings.instance.foldInactiveBlocksByDefault
                || inspectionHighlightNonIndentedNestedIfsCheckbox.isSelected != PluginSettings.instance.inspectionHighlightNonIndentedNestedIfs
                || inspectionHighlightCommentsNotMatchingIfIndentsCheckbox.isSelected != PluginSettings.instance.inspectionHighlightCommentsNotMatchingIfIndents
                || hideUnmatchedVersionsCheckbox.isSelected != PluginSettings.instance.hideUnmatchedVersions

    override fun apply() {
        PluginSettings.instance.foldAllBlocksByDefault = foldAllBlocksByDefaultCheckbox.isSelected
        PluginSettings.instance.foldInactiveBlocksByDefault = foldInactiveBlocksByDefaultCheckbox.isSelected
        PluginSettings.instance.inspectionHighlightNonIndentedNestedIfs = inspectionHighlightNonIndentedNestedIfsCheckbox.isSelected
        PluginSettings.instance.inspectionHighlightCommentsNotMatchingIfIndents = inspectionHighlightCommentsNotMatchingIfIndentsCheckbox.isSelected
        PluginSettings.instance.hideUnmatchedVersions = hideUnmatchedVersionsCheckbox.isSelected
    }

    override fun reset() {
        foldAllBlocksByDefaultCheckbox.isSelected = PluginSettings.instance.foldAllBlocksByDefault
        foldInactiveBlocksByDefaultCheckbox.isSelected = PluginSettings.instance.foldInactiveBlocksByDefault
        inspectionHighlightNonIndentedNestedIfsCheckbox.isSelected = PluginSettings.instance.inspectionHighlightNonIndentedNestedIfs
        inspectionHighlightCommentsNotMatchingIfIndentsCheckbox.isSelected = PluginSettings.instance.inspectionHighlightCommentsNotMatchingIfIndents
        hideUnmatchedVersionsCheckbox.isSelected = PluginSettings.instance.hideUnmatchedVersions
    }
}