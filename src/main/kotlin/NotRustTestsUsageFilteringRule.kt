package com.github.filteroutrusttests

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.usages.Usage
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.usages.rules.UsageFilteringRule
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.isTest
import org.rust.stdext.toPath
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.name

class NotRustTestsUsageFilteringRule : UsageFilteringRule {
    override fun getRuleId(): String = RULE_ID

    private val memo = ConcurrentHashMap<PsiElement, Boolean>()

    override fun isVisible(usage: Usage): Boolean = ReadAction.compute<Boolean, RuntimeException> {
        val path: Path = Paths.get(usage.location?.editor?.file?.path!!)
        if (path.any { it.name == "tests" }) {
            return@compute false
        }
        val psiUsage = usage as? PsiElementUsage ?: return@compute true
        val element = psiUsage.element ?: return@compute true
        if (!isRustElement(element)) return@compute true
        return@compute !shouldFilterOut(element)
    }

    private fun isRustElement(element: PsiElement): Boolean {
        val languageId = element.containingFile?.language?.id ?: return false
        return languageId == "Rust"
    }

    private fun shouldFilterOut(element: PsiElement): Boolean {
        return isInsideRustTestFunction(element, mutableSetOf())
    }

    private fun isInsideRustTestFunction(element: PsiElement, visiting: MutableSet<PsiElement>): Boolean {
        memo[element]?.let { return it }

        val filePath = File(element.project.projectFilePath ?: return false)
        for (dir in arrayOf("tests", "benches")) {
            val dir = File(dir)
            val areRelated: Boolean = filePath.getCanonicalPath().contains(dir.getCanonicalPath() + File.separator)
            if (areRelated) return true
        }

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

    private fun calculateIsInsideRustTest(element: PsiElement, visiting: MutableSet<PsiElement>): Boolean {
        if (element is RsFunction) {
            if (element.isTest) return true

            val references = ReferencesSearch.search(element)
            var hasReferences = false
            val allInTests = references.allMatch {
                hasReferences = true
                isInsideRustTestFunction(it.element, visiting)
            }
            return hasReferences && allInTests
        }

        val parent = element.parent ?: return false
        return isInsideRustTestFunction(parent, visiting)
    }

    companion object {
        const val RULE_ID: String = "com.github.filteroutrusttests.notRustTests"
    }
}
