package com.moling.tmodloaderhelper.items

import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.JBColor
import java.awt.Component
import java.awt.Graphics
import java.awt.image.BufferedImage
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.UIManager

internal data class GameBuff(val id: Int, val englishName: String, val chineseName: String) {
    val pinyin: String by lazy { PinyinSupport.toPinyin(chineseName) }
}

internal object BuffData {
    private val log = Logger.getInstance(BuffData::class.java)
    val buffs: List<GameBuff> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { loadBuffs() }
    private val iconCache = HashMap<Int, Icon>()

    fun icon(id: Int): Icon = synchronized(iconCache) {
        iconCache.getOrPut(id) {
            runCatching {
                val image = BuffData::class.java.getResourceAsStream("/assets/buffs/Buff_$id.png")?.use(ImageIO::read)
                if (image == null) MissingBuffIcon else BuffIcon(image)
            }.getOrElse { error ->
                log.debug("加载 Buff 图标失败：$id", error)
                MissingBuffIcon
            }
        }
    }

    private fun loadBuffs(): List<GameBuff> = readResource("/data/buffs.json")
        ?.let { text ->
            // 按对象逐个读取字段，不能依赖 JSON 序列化时的属性顺序。
            val objectPattern = Regex("\\{([^{}]*)}")
            val fieldPattern = Regex("\"([A-Za-z]+)\"\\s*:\\s*(?:\"((?:\\\\.|[^\"\\\\])*)\"|(\\d+))")
            objectPattern.findAll(text).mapNotNull { objectMatch ->
                val fields = fieldPattern.findAll(objectMatch.groupValues[1]).associate { field ->
                    val value = field.groupValues[2].ifEmpty { field.groupValues[3] }
                    field.groupValues[1] to unescape(value)
                }
                val id = fields["Id"]?.toIntOrNull() ?: return@mapNotNull null
                val englishName = fields["EnglishName"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val chineseName = fields["ChineseName"]?.takeIf { it.isNotBlank() } ?: englishName
                GameBuff(id, englishName, chineseName)
            }.toList()
        } ?: emptyList()

    private fun readResource(path: String): String? = runCatching {
        BuffData::class.java.getResourceAsStream(path)?.use {
            BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText().removePrefix("\uFEFF")
        }
    }.onFailure { log.warn("读取 Buff 数据失败：$path", it) }.getOrNull()

    private fun unescape(value: String): String = value
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")

    private class BuffIcon(private val image: BufferedImage) : Icon {
        private val size = 40
        override fun getIconWidth() = size
        override fun getIconHeight() = size
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val scale = minOf(1.0, size.toDouble() / image.width, size.toDouble() / image.height)
            val width = (image.width * scale).toInt().coerceAtLeast(1)
            val height = (image.height * scale).toInt().coerceAtLeast(1)
            g.drawImage(image, x + (size - width) / 2, y + (size - height) / 2, width, height, c)
        }
    }

    private object MissingBuffIcon : Icon {
        override fun getIconWidth() = 40
        override fun getIconHeight() = 40
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            g.color = UIManager.getColor("Component.borderColor") ?: JBColor.GRAY
            g.drawRoundRect(x + 7, y + 7, 25, 25, 5, 5)
            g.color = UIManager.getColor("Label.disabledForeground") ?: JBColor.GRAY
            g.drawString("?", x + 17, y + 25)
        }
    }
}
