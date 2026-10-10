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

/** Rider C# 中 NPCID. 成员的 NPC 常量补全。 */
class NpcCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                val document = parameters.editor.document
                val offset = parameters.offset.coerceAtMost(document.textLength)
                val beforeCaret = document.getText(TextRange(0, offset))
                val match = Regex("(?s)(?:\\bglobal::)?NPCID\\s*\\.\\s*([\\p{L}\\p{N}_-]*)$")
                    .find(beforeCaret) ?: return
                val typed = match.groupValues[1]
                val matcher = result.withPrefixMatcher(typed)

                NpcData.npcs.asSequence()
                    .filter { npc ->
                        typed.isBlank() || npc.englishName.contains(typed, true) ||
                            npc.chineseName.contains(typed, true) || npc.pinyin.contains(typed, true) ||
                            npc.id.toString().contains(typed)
                    }
                    .take(300)
                    .forEach { npc ->
                        matcher.addElement(
                            LookupElementBuilder.create(npc.englishName)
                                .withPresentableText(npc.englishName)
                                .withLookupString(npc.chineseName)
                                .withLookupString(npc.pinyin)
                                .withTypeText(npc.chineseName, true)
                                .withTailText("  (${npc.id})  NPCID ${npc.id}", true)
                                .withCaseSensitivity(false)
                        )
                    }
            }
        })
    }
}
