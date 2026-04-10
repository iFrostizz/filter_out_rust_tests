package com.github.filteroutrusttests

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.NonUrgentExecutor
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.isTest
import org.rust.lang.core.psi.ext.isUnderCfgTest
import org.rust.openapiext.RsPluginDisposable
import org.rust.openapiext.isUnitTestMode
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import kotlin.collections.mutableSetOf

/** Intended to be invoked from EDT */
inline fun Project.nonBlocking(element: PsiElement, crossinline uiContinuation: (Boolean) -> Unit) {
    val func = {
        val start = System.nanoTime()
        val timeoutNs = TimeUnit.SECONDS.toNanos(5)

        FilteringRuleServiceImpl.compute(element, start, timeoutNs, mutableSetOf())
    };

    if (isUnitTestMode) {
        val result = func()
        uiContinuation(result)
    } else {
        ReadAction.nonBlocking<Boolean>(Callable {
            func()
        })
            .inSmartMode(this)
            .expireWith(RsPluginDisposable.getInstance(this))
            .finishOnUiThread(ModalityState.current()) { result ->
                uiContinuation(result)
            }.submit(AppExecutorUtil.getAppExecutorService())
    }
}

@Service
internal class FilteringRuleServiceImpl : Disposable {
    companion object {
        @JvmStatic
        fun getInstance(project: Project): Disposable = project.service<RsPluginDisposable>()
    }

    internal fun compute(
        element: PsiElement,
        startNs: Long,
        timeoutNs: Long,
        visiting: MutableSet<PsiElement>
    ): Boolean {
        ProgressManager.checkCanceled()
        if (System.nanoTime() - startNs > timeoutNs) return false

        if (!visiting.add(element)) return false

        try {
            if (element.isUnderCfgTest) return true

            if (element is RsFunction) {
                if (element.isTest) return true

                ProgressManager.checkCanceled()
                if (System.nanoTime() - startNs > timeoutNs) return false

                // 🔥 REQUIRED: cancellation check before search
                ProgressManager.checkCanceled()

                val query = ReferencesSearch.search(element)

                var hasAny = false

                val allInTests = query.allMatch {
                    ProgressManager.checkCanceled()
                    if (System.nanoTime() - startNs > timeoutNs) return@allMatch false

                    hasAny = true
                    val refElement = it.element
                    compute(refElement, startNs, timeoutNs, visiting)
                }

                if (hasAny && allInTests) return true
            }

            val parent = element.parent ?: return false
            return compute(parent, startNs, timeoutNs, visiting)

        } finally {
            visiting.remove(element)
        }
    }

    override fun dispose() {}
}