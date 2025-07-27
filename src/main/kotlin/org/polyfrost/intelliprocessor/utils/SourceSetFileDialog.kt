package org.polyfrost.intelliprocessor.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Component
import java.io.File
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

class SourceSetFileDialog(
    private val project: Project,
    private val mainVersion: String,
    private val sourceFiles: List<SourceSetFile>,
    private val onFileChosen: (SourceSetFile) -> Unit
) : DialogWrapper(project) {

    private val listModel = CollectionListModel(sourceFiles)
    private val list = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int,
                isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                val item = value as SourceSetFile
                val display = item.version ?: mainVersion
                return super.getListCellRendererComponent(
                    list,
                    "${display}${File.separator}${item.classPath}",
                    index,
                    isSelected,
                    cellHasFocus
                )
            }
        }
    }

    init {
        title = "Select Preprocessed Source File"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        val search = SearchTextField()
        search.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                val filter = search.text.lowercase()
                listModel.replaceAll(sourceFiles.filter {
                    it.toRelativePath().toString().lowercase().contains(filter)
                })
            }
        })

        panel.add(search, BorderLayout.NORTH)
        panel.add(JBScrollPane(list), BorderLayout.CENTER)
        return panel
    }

    override fun doOKAction() {
        val selected = list.selectedValue ?: return
        onFileChosen(selected)
        super.doOKAction()
    }

}
