package com.github.filteroutrusttests

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.usages.Usage
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.usages.rules.UsageFilteringRule
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.isTest

class NotRustTestsUsageFilteringRule : UsageFilteringRule {
    override fun getRuleId(): String = RULE_ID

    override fun isVisible(usage: Usage): Boolean = ReadAction.compute<Boolean, RuntimeException> {
        val psiUsage = usage as? PsiElementUsage ?: return@compute true
        val element = psiUsage.element ?: return@compute true
        if (!isRustElement(element)) return@compute true
        return@compute !isInsideRustTestFunction(element)
    }

    private fun isRustElement(element: PsiElement): Boolean {
        val languageId = element.containingFile?.language?.id ?: return false
        return languageId == "Rust"
    }

    private fun isInsideRustTestFunction(element: PsiElement): Boolean {
        val parent = element.parent ?: return false
        val function = element as? RsFunction ?: return isInsideRustTestFunction(parent)
        return function.isTest
    }

    companion object {
        const val RULE_ID: String = "com.github.filteroutrusttests.notRustTests"
    }
}
