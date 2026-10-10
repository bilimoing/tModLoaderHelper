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
    private val frameCounts: Map<Int, Int> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { loadFrameCounts() }
    private val iconCache = HashMap<Int, Icon>()

    fun icon(id: Int): Icon = synchronized(iconCache) {
        iconCache.getOrPut(id) {
            runCatching {
                val image = NpcData::class.java.getResourceAsStream("/assets/npcs/NPC_$id.png")
                    ?.use(ImageIO::read)
                if (image == null) MissingNpcIcon else NpcIcon(image, frameCounts[id] ?: 1)
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

    /** 从 tModLoader Main.npcFrameCount 对应导出的资源中加载各 NPC 的纵向帧数。 */
    private fun loadFrameCounts(): Map<Int, Int> = readResource("/data/npc_frames.json")
        ?.let { text ->
            Regex("\\{\\s*\"Id\"\\s*:\\s*(\\d+)\\s*,\\s*\"FrameCount\"\\s*:\\s*(\\d+)\\s*}")
                .findAll(text).mapNotNull { match ->
                    val id = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                    val count = match.groupValues[2].toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                    id to count
                }.toMap()
        } ?: emptyMap()

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

    /** NPC 贴图按源码帧数切分，仅渲染首帧；未配置帧数的图沿用整张贴图。 */
    private class NpcIcon(private val image: BufferedImage, requestedFrames: Int) : Icon {
        private val frameCount = if (requestedFrames > 1 && image.height % requestedFrames == 0) requestedFrames else 1
        private val frameHeight = image.height / frameCount
        private val boxSize = 40
        override fun getIconWidth() = boxSize
        override fun getIconHeight() = boxSize

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val scale = minOf(1.0, boxSize.toDouble() / image.width, boxSize.toDouble() / frameHeight)
            val width = (image.width * scale).toInt().coerceAtLeast(1)
            val height = (frameHeight * scale).toInt().coerceAtLeast(1)
            val dx = x + (boxSize - width) / 2
            val dy = y + (boxSize - height) / 2
            val oldClip = g.clip
            g.clipRect(x, y, boxSize, boxSize)
            // tModLoader 将第一帧放在精灵图顶部（frame.Y = 0）。
            g.drawImage(image, dx, dy, dx + width, dy + height, 0, 0, image.width, frameHeight, c)
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
