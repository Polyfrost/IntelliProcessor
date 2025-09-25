package org.polyfrost.intelliprocessor.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridLayout
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
    private val onFileChosen: (SourceSetFile) -> Unit
) : DialogWrapper(project) {

    private val sourceFiles = sourceFilesUnsorted.sortedBy { it.versionInt }

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
                if ((value as SourceSetFile).isNonGenerated) {
                    font = font.deriveFont(Font.BOLD)
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
    private val search = SearchTextField()

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
                val filter = search.text.lowercase()
                listModel.replaceAll(sourceFiles.filter {
                    it.toString().lowercase().contains(filter)
                })

                // Improve keyboard navigation by auto-selecting the only remaining result
                if (listModel.size == 1) {
                    list.setSelectedValue(listModel.getElementAt(0), false)
                }
            }
        })

        panel.add(search, BorderLayout.NORTH)
        panel.add(JLabel(" (override) files are, non-generated, override files present in the versions/<version>/src/ directory.").apply {
                font = font.deriveFont(Font.ITALIC, 12f)
                foreground = JBColor.GRAY
            }, BorderLayout.CENTER)
        panel.add(JBScrollPane(list), BorderLayout.SOUTH)
        return panel
    }

    override fun doOKAction() {
        val selected = list.selectedValue ?: return
        onFileChosen(selected)
        super.doOKAction()
    }

}
