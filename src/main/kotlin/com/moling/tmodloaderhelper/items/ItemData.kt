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

internal data class GameItem(val id: Int, val englishName: String, val chineseName: String) {
    val pinyin: String by lazy { PinyinSupport.toPinyin(chineseName) }
}

internal object ItemData {
    private val log = Logger.getInstance(ItemData::class.java)
    val items: List<GameItem> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { loadItems() }
    private val iconCache = HashMap<Int, Icon>()

    fun icon(id: Int): Icon = synchronized(iconCache) {
        iconCache.getOrPut(id) {
            runCatching {
                val image = ItemData::class.java.getResourceAsStream("/assets/items/Item_$id.png")?.use(ImageIO::read)
                if (image != null) AnimatedItemIcon(id, image) else MissingItemIcon
            }.getOrElse { error ->
                log.debug("加载物品贴图失败：$id", error)
                MissingItemIcon
            }
        }
    }

    /** 与 Terraria.Main.InitializeItemAnimations 和 ItemID.Sets.IsFood 对应的原版动画定义。 */
    private data class AnimationSpec(
        val frameCount: Int,
        val ticksPerFrame: Int,
        val pingPong: Boolean = false,
        val static: Boolean = false,
        val scryingOrb: Boolean = false
    )

    private val foodItemIds = setOf(
        353, 357, 1787, 1911, 1912, 1919, 1920, 2266, 2267, 2268, 2425, 2426, 2427,
        3195, 3532, 4009, 4010, 4011, 4012, 4013, 4014, 4015, 4016, 4017, 4018, 4019,
        4020, 4021, 4022, 4023, 4024, 4025, 4026, 4027, 4028, 4029, 4030, 4031, 4032,
        4033, 4034, 4035, 4036, 4037, 967, 969, 4282, 4283, 4284, 4285, 4286, 4287,
        4288, 4289, 4290, 4291, 4292, 4293, 4294, 4295, 4296, 4297, 4403, 4411,
        4614, 4615, 4616, 4617, 4618, 4619, 4620, 4621, 4622, 4623, 4624, 4625,
        5009, 5042, 5041, 5092, 5093, 5275, 5277, 5278, 5537, 5645
    )

    private fun animationFor(id: Int): AnimationSpec? = when (id) {
        3581 -> AnimationSpec(4, 4)
        3580 -> AnimationSpec(4, 6)
        75 -> AnimationSpec(8, 5, pingPong = true)
        575, 547, 520, 548, 521, 549, 3453, 3454, 3455 -> AnimationSpec(4, 6)
        4068, 4069, 4070 -> AnimationSpec(4, 6, static = true)
        5644 -> AnimationSpec(9, 7, scryingOrb = true)
        in foodItemIds -> AnimationSpec(3, Int.MAX_VALUE, static = true)
        else -> null
    }

    /** 无动画注册的物品严格使用整张贴图；仅原版注册项按其帧数切分。 */
    private class AnimatedItemIcon(id: Int, private val sheet: BufferedImage) : Icon {
        private val spec = animationFor(id)
        private val frameCount = spec?.frameCount ?: 1
        private val frameHeight = if (spec != null && sheet.height % frameCount == 0) {
            sheet.height / frameCount
        } else sheet.height
        private val effectiveFrameCount = if (frameHeight == sheet.height) 1 else frameCount
        private val boxSize = 40
        private var elapsedTicks = 0
        private var frame = 0
        private var orbStateTicks = 0
        private var orbMoving = true
        private var orbDirection = 1
        private var component: Component? = null
        private val timer = javax.swing.Timer(50) {
            val animation = spec
            if (animation != null && !animation.static && effectiveFrameCount > 1) {
                elapsedTicks++
                if (elapsedTicks >= animation.ticksPerFrame) {
                    elapsedTicks = 0
                    if (animation.scryingOrb) updateOrb(animation.frameCount)
                    else if (animation.pingPong) frame = (frame + 1) % (animation.frameCount * 2 - 2)
                    else frame = (frame + 1) % animation.frameCount
                    component?.repaint()
                }
            }
        }

        private fun updateOrb(count: Int) {
            if (--orbStateTicks <= 0) {
                if (orbMoving) {
                    orbMoving = false
                    orbStateTicks = 7
                    frame = if (kotlin.random.Random.nextInt(4) == 0) 0 else frame
                } else {
                    orbMoving = true
                    orbStateTicks = 5
                    if (kotlin.random.Random.nextInt(4) == 0) orbDirection *= -1
                }
            } else if (orbMoving) {
                frame += orbDirection
                if (frame >= count) frame = 1
                else if (frame <= 0) frame = count - 1
            }
        }

        override fun getIconWidth() = boxSize
        override fun getIconHeight() = boxSize

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            component = c
            if (spec != null && !spec.static && effectiveFrameCount > 1 && !timer.isRunning) timer.start()
            val sourceWidth = sheet.width
            val sourceHeight = frameHeight
            val scale = minOf(1.0, boxSize.toDouble() / sourceWidth, boxSize.toDouble() / sourceHeight)
            val drawWidth = (sourceWidth * scale).toInt().coerceAtLeast(1)
            val drawHeight = (sourceHeight * scale).toInt().coerceAtLeast(1)
            val drawX = x + (boxSize - drawWidth) / 2
            val drawY = y + (boxSize - drawHeight) / 2
            val oldClip = g.clip
            g.clipRect(x, y, boxSize, boxSize)
            val displayedFrame = if (spec?.pingPong == true && frame >= frameCount) {
                frameCount * 2 - 2 - frame
            } else frame.coerceIn(0, effectiveFrameCount - 1)
            val sourceY = displayedFrame * frameHeight
            val sourceFrameHeight = (frameHeight - if (spec != null) 2 else 0).coerceAtLeast(1)
            g.drawImage(sheet, drawX, drawY, drawX + drawWidth, drawY + drawHeight,
                0, sourceY, sourceWidth, (sourceY + sourceFrameHeight).coerceAtMost(sheet.height), c)
            g.clip = oldClip
        }
    }

    private fun loadItems(): List<GameItem> {
        val stream = ItemData::class.java.getResourceAsStream("/data/items.json")
            ?: run { log.warn("缺少提取后的物品数据 /data/items.json"); return emptyList() }
        return runCatching {
            val text = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText().removePrefix("\uFEFF") }
            val objectPattern = Regex("\\{\\s*\"Id\"\\s*:\\s*(-?\\d+)\\s*,\\s*\"EnglishName\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,\\s*\"ChineseName\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*}")
            objectPattern.findAll(text).mapNotNull { match ->
                val id = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                GameItem(id, unescape(match.groupValues[2]), unescape(match.groupValues[3]))
            }.toList()
        }.getOrElse { error ->
            log.error("读取物品数据失败", error)
            emptyList()
        }
    }

    private fun unescape(value: String): String = value
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")


    private object MissingItemIcon : Icon {
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
