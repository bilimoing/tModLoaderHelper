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

internal data class GameProjectile(val id: Int, val englishName: String, val chineseName: String) {
    val pinyin: String by lazy { PinyinSupport.toPinyin(chineseName) }
}

internal object ProjectileData {
    private val log = Logger.getInstance(ProjectileData::class.java)
    val projectiles: List<GameProjectile> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { loadProjectiles() }
    private val frames: Map<Int, Int> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { loadFrames() }
    private val iconCache = HashMap<Int, Icon>()

    fun icon(id: Int): Icon = synchronized(iconCache) {
        iconCache.getOrPut(id) {
            runCatching {
                val image = ProjectileData::class.java
                    .getResourceAsStream("/assets/projectiles/Projectile_$id.png")?.use(ImageIO::read)
                if (image == null) MissingProjectileIcon else ProjectileIcon(image, frames[id] ?: 1)
            }.getOrElse { error ->
                log.debug("加载弹幕贴图失败：$id", error)
                MissingProjectileIcon
            }
        }
    }

    private fun loadProjectiles(): List<GameProjectile> = readResource("/data/projectiles.json")
        ?.let { text ->
            val pattern = Regex("\\{\\s*\"Id\"\\s*:\\s*(\\d+)\\s*,\\s*\"EnglishName\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,\\s*\"ChineseName\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*}")
            pattern.findAll(text).mapNotNull { match ->
                val id = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                GameProjectile(id, unescape(match.groupValues[2]), unescape(match.groupValues[3]))
            }.toList()
        } ?: emptyList()

    private fun loadFrames(): Map<Int, Int> = readResource("/data/projectile_frames.json")
        ?.let { text ->
            Regex("\\{\\s*\"Id\"\\s*:\\s*(\\d+)\\s*,\\s*\"FrameCount\"\\s*:\\s*(\\d+)\\s*}")
                .findAll(text).mapNotNull { m ->
                    val id = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                    val count = m.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                    id to count
                }.toMap()
        } ?: emptyMap()

    private fun readResource(path: String): String? = runCatching {
        ProjectileData::class.java.getResourceAsStream(path)?.use {
            BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText().removePrefix("\uFEFF")
        }
    }.onFailure { log.warn("读取弹幕数据失败：$path", it) }.getOrNull()

    private fun unescape(value: String): String = value
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")

    /**
     * 弹幕预览始终显示原版贴图的第一帧，不播放动画。
     * requestedFrames 仍用于按 tModLoader 的 projFrames 切分精灵表，避免把整张动画图压缩成一帧。
     */
    private class ProjectileIcon(private val sheet: BufferedImage, requestedFrames: Int) : Icon {
        private val frameCount = if (requestedFrames > 1 && sheet.height % requestedFrames == 0) requestedFrames else 1
        private val frameHeight = sheet.height / frameCount
        private val boxSize = 40

        override fun getIconWidth() = boxSize
        override fun getIconHeight() = boxSize

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val scale = minOf(1.0, boxSize.toDouble() / sheet.width, boxSize.toDouble() / frameHeight)
            val width = (sheet.width * scale).toInt().coerceAtLeast(1)
            val height = (frameHeight * scale).toInt().coerceAtLeast(1)
            val dx = x + (boxSize - width) / 2
            val dy = y + (boxSize - height) / 2
            val oldClip = g.clip
            g.clipRect(x, y, boxSize, boxSize)
            // 与源码中的 proj.frame 初始值一致，固定绘制第一帧（frame = 0）。
            g.drawImage(sheet, dx, dy, dx + width, dy + height, 0, 0, sheet.width, frameHeight, c)
            g.clip = oldClip
        }
    }

    private object MissingProjectileIcon : Icon {
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
