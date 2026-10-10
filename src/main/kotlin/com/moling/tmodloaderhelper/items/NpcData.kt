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

internal data class GameNpc(val id: Int, val englishName: String, val chineseName: String) {
    val pinyin: String by lazy { PinyinSupport.toPinyin(chineseName) }
}

internal object NpcData {
    private val log = Logger.getInstance(NpcData::class.java)
    val npcs: List<GameNpc> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { loadNpcs() }
    private val iconCache = HashMap<Int, Icon>()

    fun icon(id: Int): Icon = synchronized(iconCache) {
        iconCache.getOrPut(id) {
            runCatching {
                val image = NpcData::class.java.getResourceAsStream("/assets/npcs/NPC_$id.png")
                    ?.use(ImageIO::read)
                if (image == null) MissingNpcIcon else NpcIcon(image)
            }.getOrElse { error ->
                log.debug("加载 NPC 贴图失败：$id", error)
                MissingNpcIcon
            }
        }
    }

    private fun loadNpcs(): List<GameNpc> {
        val itemNamesByEnglish by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            ItemData.items.associate { it.englishName to it.chineseName }
        }
        val text = readResource("/data/npcs.json") ?: return emptyList()
        val pattern = Regex("\\{\\s*\"Id\"\\s*:\\s*(-?\\d+)\\s*,\\s*\"EnglishName\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,\\s*\"ChineseName\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*}")
        return pattern.findAll(text).mapNotNull { match ->
            val id = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val englishName = unescape(match.groupValues[2])
            val rawChineseName = unescape(match.groupValues[3])
            val reference = rawChineseName.removePrefix("{$").removeSuffix("}")
                .takeIf { rawChineseName.startsWith("{$" + "ItemName.") }
                ?.removePrefix("ItemName.")
            val chineseName = reference?.let(itemNamesByEnglish::get)
                ?.takeIf { it.isNotBlank() } ?: rawChineseName
            GameNpc(id, englishName, chineseName)
        }.toList()
    }

    private fun readResource(path: String): String? = runCatching {
        NpcData::class.java.getResourceAsStream(path)?.use {
            BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText().removePrefix("\uFEFF")
        }
    }.onFailure { log.warn("读取 NPC 数据失败：$path", it) }.getOrNull()

    private fun unescape(value: String): String = value
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")

    private class NpcIcon(private val image: BufferedImage) : Icon {
        private val boxSize = 40
        override fun getIconWidth() = boxSize
        override fun getIconHeight() = boxSize

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val scale = minOf(1.0, boxSize.toDouble() / image.width, boxSize.toDouble() / image.height)
            val width = (image.width * scale).toInt().coerceAtLeast(1)
            val height = (image.height * scale).toInt().coerceAtLeast(1)
            val dx = x + (boxSize - width) / 2
            val dy = y + (boxSize - height) / 2
            val oldClip = g.clip
            g.clipRect(x, y, boxSize, boxSize)
            g.drawImage(image, dx, dy, width, height, c)
            g.clip = oldClip
        }
    }

    private object MissingNpcIcon : Icon {
        override fun getIconWidth() = 32
        override fun getIconHeight() = 32
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            g.color = UIManager.getColor("Component.borderColor") ?: JBColor.GRAY
            g.drawRoundRect(x + 3, y + 3, 25, 25, 5, 5)
            g.color = UIManager.getColor("Label.disabledForeground") ?: JBColor.GRAY
            g.drawString("?", x + 13, y + 21)
        }
    }
}
