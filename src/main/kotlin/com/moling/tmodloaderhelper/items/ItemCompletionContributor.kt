package com.moling.tmodloaderhelper.items

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/** Rider C# 中 ItemID. 成员的物品常量补全。 */
class ItemCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                val document = parameters.editor.document
                val offset = parameters.offset.coerceAtMost(document.textLength)
                val beforeCaret = document.getText(TextRange(0, offset))
                val memberMatch = Regex("(?s)(?:\\bglobal::)?ItemID\\s*\\.\\s*([\\p{L}\\p{N}_]*)$").find(beforeCaret)
                    ?: return
                val typed = memberMatch.groupValues[1]
                val matcher = result.withPrefixMatcher(typed)

                ItemData.items.asSequence()
                    .filter { item ->
                        typed.isBlank() ||
                            item.englishName.contains(typed, ignoreCase = true) ||
                            item.chineseName.contains(typed, ignoreCase = true) ||
                            item.pinyin.contains(typed, ignoreCase = true) ||
                            item.id.toString().contains(typed)
                    }
                    .take(250)
                    .forEach { item ->
                        matcher.addElement(
                            LookupElementBuilder.create(item.englishName)
                                .withPresentableText(item.englishName)
                                // 让 Rider 的前缀匹配器也能用拼音/中文匹配，而不只是用英文常量名匹配。
                                .withLookupString(item.chineseName)
                                .withLookupString(item.pinyin)
                                .withTypeText(item.chineseName, true)
                                .withTailText("  (${item.id})  ItemID ${item.id}", true)
                                .withCaseSensitivity(false)
                        )
                    }
            }
        })
    }
}
