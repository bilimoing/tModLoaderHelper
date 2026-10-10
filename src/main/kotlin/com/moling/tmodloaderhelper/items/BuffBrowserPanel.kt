package com.moling.tmodloaderhelper.items

import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import javax.swing.DefaultListModel
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.UIManager
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

internal class BuffBrowserPanel : JBPanel<BuffBrowserPanel>(BorderLayout()) {
    private val search = JBTextField()
    private val model = DefaultListModel<GameBuff>()
    private val list = JBList(model)

    init {
        border = JBUI.Borders.empty(8)
        val header = JPanel(BorderLayout(6, 0))
        header.border = JBUI.Borders.emptyBottom(8)
        header.add(JLabel("搜索 Buff"), BorderLayout.WEST)
        search.emptyText.text = "输入英文名、中文名、拼音或 ID"
        header.add(search, BorderLayout.CENTER)
        add(header, BorderLayout.NORTH)
        list.cellRenderer = BuffRenderer()
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.layoutOrientation = JList.HORIZONTAL_WRAP
        list.visibleRowCount = 0
        list.fixedCellWidth = 100
        list.fixedCellHeight = 90
        list.border = JBUI.Borders.empty(6)
        add(ScrollPaneFactory.createScrollPane(list), BorderLayout.CENTER)
        search.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = refresh()
            override fun removeUpdate(e: DocumentEvent) = refresh()
            override fun changedUpdate(e: DocumentEvent) = refresh()
        })
        refresh()
    }

    private fun refresh() {
        val query = search.text.trim()
        val filtered = if (query.isEmpty()) BuffData.buffs else BuffData.buffs.filter {
            it.englishName.contains(query, true) || it.chineseName.contains(query, true) ||
                it.pinyin.contains(query, true) || it.id.toString().contains(query)
        }
        model.clear()
        filtered.forEach(model::addElement)
    }

    private class BuffRenderer : JPanel(GridLayout(2, 1, 0, 1)), ListCellRenderer<GameBuff> {
        private val iconLabel = JLabel()
        private val nameLabel = JLabel("", SwingConstants.CENTER)

        init {
            border = JBUI.Borders.empty(4)
            iconLabel.horizontalAlignment = SwingConstants.CENTER
            nameLabel.font = nameLabel.font.deriveFont(10f)
            add(iconLabel)
            add(nameLabel)
            preferredSize = Dimension(100, 82)
            isOpaque = true
        }

        override fun getListCellRendererComponent(
            list: JList<out GameBuff>, value: GameBuff, index: Int,
            selected: Boolean, focused: Boolean
        ): JPanel {
            iconLabel.icon = BuffData.icon(value.id)
            nameLabel.text = value.chineseName
            toolTipText = "<html>${value.chineseName}<br>${value.englishName}<br>BuffID: ${value.id}</html>"
            background = if (selected) list.selectionBackground else UIManager.getColor("Panel.background")
            return this
        }
    }
}
