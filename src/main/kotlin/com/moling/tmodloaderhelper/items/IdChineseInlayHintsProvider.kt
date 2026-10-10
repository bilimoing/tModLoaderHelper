package com.moling.tmodloaderhelper.items

import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.BorderLayout

/** 在 ItemID、ProjectileID、NPCID、BuffID 常量后以内嵌提示显示中文名称。 */
class IdChineseInlayHintsProvider : InlayHintsProvider<IdChineseInlayHintsProvider.Settings> {
    data class Settings(var enabled: Boolean = true)

    override val name = "tModLoader ID 中文名称"
    override val description = "在 C# 代码中的 ItemID、ProjectileID、NPCID、BuffID 常量后显示中文名称"
    override val previewText = "player.AddBuff(BuffID.IceBarrier, 5);"
    override val key = SettingsKey<Settings>("tmodloader.id.chinese.inlay")
    override fun createSettings() = Settings()
    override fun isLanguageSupported(language: Language) = language.id == "C#"

    override fun getCollectorFor(file: PsiFile, editor: Editor, settings: Settings, sink: InlayHintsSink) =
        object : FactoryInlayHintsCollector(editor) {
            private var scanned = false

            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (scanned || !settings.enabled) return true
                scanned = true

                val text = editor.document.text
                val pattern = Regex("\\b(ItemID|ProjectileID|NPCID|BuffID)\\s*\\.\\s*([A-Za-z_][A-Za-z0-9_]*)")
                val lineComments = Regex("//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'")
                    .findAll(text)
                    .map { it.range }
                    .toList()

                pattern.findAll(text).forEach { match ->
                    // 仅给代码表达式加提示；跳过注释和字符串，避免注释文本里的 ID 重复显示。
                    if (lineComments.any { match.range.first in it }) return@forEach
                    val entry = findEntry(match.groupValues[1], match.groupValues[2]) ?: return@forEach

                    // 按 Rider 参数名提示的布局，把中文放在表达式前方：中文名: ItemID.Xxx。
                    // 这样不会把中文错误地挂在常量名末尾，也能与 stack: 200 等提示对齐。
                    sink.addInlineElement(
                        match.range.first,
                        false,
                        factory.offsetFromTopForSmallText(
                            factory.text("${entry.chineseName}: ")
                        ),
                        false
                    )
                }
                return true
            }
        }

    override fun createConfigurable(settings: Settings): ImmediateConfigurable = object : ImmediateConfigurable {
        override fun createComponent(changeListener: com.intellij.codeInsight.hints.ChangeListener): JComponent {
            val checkbox = JCheckBox("显示 ID 中文名称", settings.enabled)
            checkbox.addActionListener {
                settings.enabled = checkbox.isSelected
                changeListener.settingsChanged()
            }
            return JPanel(BorderLayout()).apply { add(checkbox, BorderLayout.WEST) }
        }
    }

    private data class Entry(val chineseName: String)

    private fun findEntry(owner: String, name: String): Entry? = when (owner) {
        "ItemID" -> ItemData.items.firstOrNull { it.englishName == name }?.let { Entry(it.chineseName) }
        "ProjectileID" -> ProjectileData.projectiles.firstOrNull { it.englishName == name }?.let { Entry(it.chineseName) }
        "NPCID" -> NpcData.npcs.firstOrNull { it.englishName == name }?.let { Entry(it.chineseName) }
        "BuffID" -> BuffData.buffs.firstOrNull { it.englishName == name }?.let { Entry(it.chineseName) }
        else -> null
    }
}
