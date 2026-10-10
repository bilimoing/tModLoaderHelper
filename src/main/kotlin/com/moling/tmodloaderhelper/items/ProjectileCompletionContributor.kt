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

/** Rider C# 中 ProjectileID. 成员的弹幕常量补全。 */
class ProjectileCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                val document = parameters.editor.document
                val offset = parameters.offset.coerceAtMost(document.textLength)
                val beforeCaret = document.getText(TextRange(0, offset))
                val match = Regex("(?s)(?:\\bglobal::)?ProjectileID\\s*\\.\\s*([\\p{L}\\p{N}_]*)$")
                    .find(beforeCaret) ?: return
                val typed = match.groupValues[1]
                val matcher = result.withPrefixMatcher(typed)

                ProjectileData.projectiles.asSequence()
                    .filter { projectile ->
                        typed.isBlank() || projectile.englishName.contains(typed, true) ||
                            projectile.chineseName.contains(typed, true) || projectile.pinyin.contains(typed, true) ||
                            projectile.id.toString().contains(typed)
                    }
                    .take(300)
                    .forEach { projectile ->
                        matcher.addElement(
                            LookupElementBuilder.create(projectile.englishName)
                                .withPresentableText(projectile.englishName)
                                .withLookupString(projectile.chineseName)
                                .withLookupString(projectile.pinyin)
                                .withTypeText(projectile.chineseName, true)
                                .withTailText("  (${projectile.id})  ProjectileID ${projectile.id}", true)
                                .withCaseSensitivity(false)
                        )
                    }
            }
        })
    }
}
