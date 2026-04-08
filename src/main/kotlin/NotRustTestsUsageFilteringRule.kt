package com.github.filteroutrusttests

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.usages.Usage
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.usages.rules.UsageFilteringRule
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.isTest
import org.rust.lang.core.psi.ext.isUnderCfgTest
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.name

class NotRustTestsUsageFilteringRule : UsageFilteringRule {
    override fun getRuleId(): String = RULE_ID

    private val memo = ConcurrentHashMap<PsiElement, Boolean>()

    override fun isVisible(usage: Usage): Boolean = ReadAction.compute<Boolean, RuntimeException> {
        val psiUsage = usage as? PsiElementUsage ?: return@compute true
        val element = psiUsage.element ?: return@compute true

        if (isInTestLikeDir(element)) return@compute false

        if (!isRustElement(element)) return@compute true

        return@compute !shouldFilterOut(element)
    }

    private fun isRustElement(element: PsiElement): Boolean {
        val languageId = element.containingFile?.language?.id ?: return false
        return languageId == "Rust"
    }

    private fun isInTestLikeDir(element: PsiElement): Boolean {
        val path = element.containingFile?.virtualFile?.path ?: return false
        val p = Paths.get(path)
        return p.any { it.name == "tests" || it.name == "benches" }
    }

    private fun shouldFilterOut(element: PsiElement): Boolean {
        return isInsideRustTestFunction(element, mutableSetOf())
    }

    private fun isInsideRustTestFunction(
        element: PsiElement, visiting: MutableSet<PsiElement>
    ): Boolean {
        memo[element]?.let { return it }

        if (visiting.contains(element)) return false

        visiting.add(element)
        try {
            val result = calculateIsInsideRustTest(element, visiting)
            memo[element] = result
            return result
        } finally {
            visiting.remove(element)
        }
    }

    private fun calculateIsInsideRustTest(
        element: PsiElement, visiting: MutableSet<PsiElement>
    ): Boolean {
        if (element.isUnderCfgTest) return true

        if (element is RsFunction) {
            if (element.isTest) return true

            val references = ReferencesSearch.search(element)

            val allInTests = references.allMatch {
                isInsideRustTestFunction(it.element, visiting)
            }

            if (references.count() > 0 && allInTests) return true
        }

        val parent = element.parent ?: return false
        return isInsideRustTestFunction(parent, visiting)
    }

    companion object {
        const val RULE_ID: String = "com.github.filteroutrusttests.notRustTests"
    }
}