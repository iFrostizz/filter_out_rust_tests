package com.github.filteroutrusttests

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.usages.Usage
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.usages.rules.UsageFilteringRule
import kotlin.jvm.java


class NotRustTestsUsageFilteringRule : UsageFilteringRule {
    override fun getRuleId(): String = RULE_ID

    override fun isVisible(usage: Usage): Boolean = ReadAction.compute<Boolean, RuntimeException> {
        val psiUsage = usage as? PsiElementUsage ?: return@compute true
        val element = psiUsage.element ?: return@compute true

        if (isInTestLikeDir(element)) return@compute false
        if (!isRustElement(element)) return@compute true

        val service: FilteringRuleService =
            ApplicationManager.getApplication().getService(FilteringRuleService::class.java)

        val cached = service.getCached(element)
        if (cached != null) {
            return@compute !cached
        }

        service.schedule(element)

        return@compute true
    }

    private fun isRustElement(element: PsiElement): Boolean {
        return element.containingFile?.language?.id == "Rust"
    }

    private fun isInTestLikeDir(element: PsiElement): Boolean {
        val path = element.containingFile?.virtualFile?.path ?: return false
        return path.contains("/tests/") || path.contains("/benches/")
    }

    companion object {
        const val RULE_ID = "com.github.filteroutrusttests.notRustTests"
    }
}