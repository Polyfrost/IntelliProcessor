package org.polyfrost.intelliprocessor.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import org.polyfrost.intelliprocessor.config.PluginSettings
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.GridLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

class SourceSetFileDialog(
    project: Project,
    sourceFilesUnsorted: List<SourceSetFile>,
    private val hadConditions: Boolean,
    private val onFileChosen: (SourceSetFile) -> Unit,
) : DialogWrapper(project) {

    // Sort entries via int version as 1.8.9 will order before 1.12.2 otherwise
    private val sourceFiles = sourceFilesUnsorted.sortedBy { it.version.mc }

    private val listModel = CollectionListModel(sourceFiles)
    private val list = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = ListCellRenderer<Any> { list, value, index, isSelected, cellHasFocus ->
            // Use DefaultListCellRenderer to get selection colors etc
            fun String.label() = DefaultListCellRenderer().getListCellRendererComponent(
                list,
                this,
                index,
                isSelected,
                cellHasFocus
            ).apply {
                // Further differentiate preprocessed generated files with font style
                (value as? SourceSetFile)?.let {
                    if (it.isNonGenerated) {
                        font = font.deriveFont(Font.BOLD)
                    }
                    if (!it.metOpeningCondition) foreground = JBColor.GRAY
                }
            }

            JPanel(GridLayout(1, 2)).apply {
                val stringParts = value.toString().split("|")
                add(JPanel(GridLayout(1, 2)).apply {
                    add(" ${stringParts[0]}".label())
                    add("| ${stringParts[1]}".label())
                })
                add("| ${stringParts[2]}".label())
            }
        }

        // Double click also triggers doOKAction()
        addMouseListener(object : MouseAdapter() {
            private var lastClickTime = 0L
            private var lastSelectedIndex = -1

            override fun mouseClicked(e: MouseEvent?) {
                if (e?.button != MouseEvent.BUTTON1) return

                val clickTime = System.currentTimeMillis()
                if (selectedIndex == lastSelectedIndex && clickTime - lastClickTime < 1000) { // 1 second threshold
                    doOKAction()
                }
                lastClickTime = clickTime
                lastSelectedIndex = selectedIndex
            }
        })
    }

    private val search = SearchTextField().apply {
        // Keyboard navigation: Down arrow focuses into the list from the search field
        textEditor.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent?) {
                if (e?.keyCode == KeyEvent.VK_DOWN || e?.keyCode == KeyEvent.VK_KP_DOWN) {
                    if (list.selectedValue == null) {
                        list.selectedIndex = 0
                    }
                    list.requestFocusInWindow()
                    e.consume()
                }
            }
        })
    }

    init {
        title = "Select Preprocessed Source File"
        init()
    }

    // Focus the search field when the dialog is first opened, streamlines keyboard navigation
    override fun getPreferredFocusedComponent(): JComponent? = search

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())

        search.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                filterList()
            }
        })

        panel.add(search, BorderLayout.NORTH)
        panel.add(JBScrollPane(list), BorderLayout.CENTER)
        bottomPanelOrNull()?.let {
            panel.add(it,BorderLayout.SOUTH)
        }
        return panel
    }

    private fun bottomPanelOrNull(): JPanel? {
        fun String.label(): JLabel = JLabel(this).apply {
            font = font.deriveFont(Font.ITALIC, 12f)
            foreground = JBColor.GRAY
        }

        val belowList = mutableListOf<Component>()
        if (sourceFiles.any { it.subVersion != null && it.isNonGenerated }) {
            belowList.add(" - (override) files are, non-generated, override files present in the versions/<version>/src/ directory.".label())
        }
        if (!hadConditions) {
            belowList.add(" - No preprocessor conditions were found to apply at the caret position to test results with. Showing all.".label())
        } else if (sourceFiles.any { !it.metOpeningCondition }) {
            val hide = PluginSettings.instance.hideUnmatchedVersions
            val hideText =
                " - Results that do not meet the preprocessor conditions at the caret position have been hidden."
            val showText =
                " - Faded results are those that do not meet the preprocessor conditions at the caret position."
            val label = (if (hide) hideText else showText).label()
            belowList.add(label)
            belowList.add(CheckBox("Hide results that do not meet preprocessor conditions at caret", hide).apply {
                addActionListener {
                    PluginSettings.modify { hideUnmatchedVersions = isSelected }
                    label.text = if (isSelected) hideText else showText
                    filterList()
                }
            })
            filterList()
        }

        return if (belowList.isEmpty()) null else JPanel(GridLayout(belowList.size, 1)).apply {
            for (below in belowList) {
                add(below, BorderLayout.NORTH)
            }
        }
    }

    private fun filterList() {
        val filter = search.text.lowercase()
        listModel.replaceAll(sourceFiles.filter {
            it.toString().lowercase().contains(filter)
                    // Hide entries that do not meet the opening condition if the setting is enabled
                    &&  (!PluginSettings.instance.hideUnmatchedVersions || it.metOpeningCondition)
        })

        if (filter.isEmpty() || listModel.isEmpty) {
            list.setSelectedValue(null, false)
        } else {
            // Improve keyboard navigation by auto-selecting the first result
            list.setSelectedValue(listModel.getElementAt(0), false)
        }
    }

    override fun doOKAction() {
        val selected = list.selectedValue ?: return
        onFileChosen(selected)
        super.doOKAction()
    }

}
