package com.github.filteroutrusttests

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.usages.Usage
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.usages.rules.UsageFilteringRule
import com.intellij.util.concurrency.NonUrgentExecutor
import io.netty.util.BooleanSupplier
import kotlinx.datetime.Instant
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.isTest
import org.rust.lang.core.psi.ext.isUnderCfgTest
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.name
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class NotRustTestsUsageFilteringRule : UsageFilteringRule {
    override fun getRuleId(): String = RULE_ID

    private val memo = ConcurrentHashMap<PsiElement, Boolean>()
    private var lastClearTime: Long = System.currentTimeMillis()

    override fun isVisible(usage: Usage): Boolean = ReadAction.compute<Boolean, RuntimeException> {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClearTime > 1000 * 60) { // Clear memo every minute
            memo.clear()
            lastClearTime = currentTime
        }

        val psiUsage = usage as? PsiElementUsage ?: return@compute true
        val element = psiUsage.element ?: return@compute true

        if (isInTestLikeDir(element)) return@compute false

        if (!isRustElement(element)) return@compute true

        val startTime = System.currentTimeMillis()
        return@compute !shouldFilterOut(element, startTime)
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

    private fun shouldFilterOut(element: PsiElement, startTime: Long): Boolean {
        return isInsideRustTestFunction(element, mutableSetOf(), startTime, 0)
    }

    private fun isInsideRustTestFunction(
        element: PsiElement, visiting: MutableSet<PsiElement>, startTime: Long, depth: Int
    ): Boolean {
        ProgressManager.checkCanceled()

        if (depth > MAX_DEPTH || System.currentTimeMillis() - startTime > TIMEOUT_MS) {
            return false
        }

        memo[element]?.let { return it }

        if (visiting.contains(element)) return false

        visiting.add(element)
        try {
            val result = calculateIsInsideRustTest(element, visiting, startTime, depth)
            memo[element] = result
            return result
        } finally {
            visiting.remove(element)
        }
    }

    private fun calculateIsInsideRustTest(
        element: PsiElement, visiting: MutableSet<PsiElement>, startTime: Long, depth: Int
    ): Boolean {
        if (element.isUnderCfgTest) return true

        if (element is RsFunction) {
            if (element.isTest) return true

            val references = ReferencesSearch.search(element)

            val allInTests = references.allMatch {
                isInsideRustTestFunction(it.element, visiting, startTime, depth + 1)
            }

            if (references.count() > 0 && allInTests) return true
        }

        val parent = element.parent ?: return false
        return isInsideRustTestFunction(parent, visiting, startTime, depth + 1)
    }

    companion object {
        const val RULE_ID: String = "com.github.filteroutrusttests.notRustTests"
        const val MAX_DEPTH: Int = 20
        const val TIMEOUT_MS: Long = 500
    }
}