package com.moling.tmodloaderhelper.items

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/** Rider C# 中 BuffID. 成员的 Buff 常量补全。 */
class BuffCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                val document = parameters.editor.document
                val offset = parameters.offset.coerceAtMost(document.textLength)
                val beforeCaret = document.getText(TextRange(0, offset))
                val match = Regex("(?s)(?:\\bglobal::)?BuffID\\s*\\.\\s*([\\p{L}\\p{N}_]*)$")
                    .find(beforeCaret) ?: return
                val typed = match.groupValues[1]
                val matcher = result.withPrefixMatcher(typed)
                BuffData.buffs.asSequence()
                    .filter { buff ->
                        typed.isBlank() || buff.englishName.contains(typed, true) ||
                            buff.chineseName.contains(typed, true) || buff.pinyin.contains(typed, true) ||
                            buff.id.toString().contains(typed)
                    }
                    .take(300)
                    .forEach { buff ->
                        matcher.addElement(
                            LookupElementBuilder.create(buff.englishName)
                                .withPresentableText(buff.englishName)
                                .withLookupString(buff.chineseName)
                                .withLookupString(buff.pinyin)
                                .withTypeText(buff.chineseName, true)
                                .withTailText("  (${buff.id})  BuffID ${buff.id}", true)
                                .withCaseSensitivity(false)
                        )
                    }
            }
        })
    }
}
