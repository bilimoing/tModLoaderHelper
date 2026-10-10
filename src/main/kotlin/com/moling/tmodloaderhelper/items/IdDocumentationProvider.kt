package com.moling.tmodloaderhelper.items

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement

/** 为 ItemID、ProjectileID、NPCID、BuffID 下的所有已收录常量提供中文快速文档。 */
class IdDocumentationProvider : AbstractDocumentationProvider() {
    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? =
        resolve(element, originalElement)?.let { (owner, entry) ->
            "<b>${owner}.${entry.englishName}</b><br>${entry.chineseName}<br>ID: ${entry.id}"
        }

    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?): String? =
        resolve(element, originalElement)?.let { (owner, entry) ->
            format(owner, entry.englishName, entry.chineseName, entry.id)
        }

    private fun resolve(element: PsiElement, originalElement: PsiElement?): Pair<String, IdEntry>? {
        val constantName = sequenceOf(element, originalElement)
            .filterNotNull()
            .flatMap { candidate ->
                sequenceOf(candidate.text.trim().removeSurrounding("@"), candidate.parent?.text?.trim().orEmpty())
            }
            .map { it.substringAfterLast('.').trim() }
            .firstOrNull { it.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) }
            ?: return null
        val owner = findOwner(element, originalElement) ?: return null
        val entry = when (owner) {
            "ItemID" -> ItemData.items.firstOrNull { it.englishName == constantName }
                ?.let { IdEntry(it.englishName, it.chineseName, it.id) }
            "ProjectileID" -> ProjectileData.projectiles.firstOrNull { it.englishName == constantName }
                ?.let { IdEntry(it.englishName, it.chineseName, it.id) }
            "NPCID" -> NpcData.npcs.firstOrNull { it.englishName == constantName }
                ?.let { IdEntry(it.englishName, it.chineseName, it.id) }
            "BuffID" -> BuffData.buffs.firstOrNull { it.englishName == constantName }
                ?.let { IdEntry(it.englishName, it.chineseName, it.id) }
            else -> null
        } ?: return null
        return owner to entry
    }

    private fun findOwner(element: PsiElement, originalElement: PsiElement?): String? {
        val ownerPattern = Regex("(?:global::)?(ItemID|ProjectileID|NPCID|BuffID)\\s*\\.")
        sequenceOf(element, originalElement).filterNotNull().forEach { start ->
            var current: PsiElement? = start
            repeat(8) {
                ownerPattern.find(current?.text.orEmpty())?.let { return it.groupValues[1] }
                current = current?.parent
            }
        }
        return null
    }

    private data class IdEntry(val englishName: String, val chineseName: String, val id: Int)

    private fun format(owner: String, englishName: String, chineseName: String, id: Int): String =
        "$owner.$englishName — $chineseName（ID: $id）"
}
