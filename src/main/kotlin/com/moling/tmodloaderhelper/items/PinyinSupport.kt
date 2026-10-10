package com.moling.tmodloaderhelper.items

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

/** 所有图鉴条目共享的拼音转换器，统一去声调并使用小写。 */
internal object PinyinSupport {
    private val format = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITHOUT_TONE
        vCharType = HanyuPinyinVCharType.WITH_V
    }

    fun toPinyin(value: String): String = runCatching {
        buildString {
            value.forEach { char ->
                append(PinyinHelper.toHanyuPinyinStringArray(char, format)?.firstOrNull() ?: char.toString())
            }
        }.replace(Regex("[0-9]"), "").lowercase()
    }.getOrDefault(value.lowercase())
}
