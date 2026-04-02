package dev.plugin.filteroutrusttests

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.Project
import com.intellij.ide.util.PropertiesComponent
import com.intellij.usages.PsiElementUsageTarget
import com.intellij.usages.UsageView
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.usages.rules.UsageFilteringRule
import com.intellij.usages.rules.UsageFilteringRuleProvider

class NotRustTestsUsageFilteringRuleProvider : UsageFilteringRuleProvider {
    override fun getApplicableRules(project: Project): Collection<UsageFilteringRule> {
        // The action is provided via createFilteringActions(view) to keep normal toggle semantics.
        return emptyList()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getActiveRules(project: Project): Array<UsageFilteringRule> {
        return if (isEnabled(project)) arrayOf(NotRustTestsUsageFilteringRule()) else UsageFilteringRule.EMPTY_ARRAY
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun createFilteringActions(view: UsageView): Array<com.intellij.openapi.actionSystem.AnAction> {
        return arrayOf(NotRustTestsFilterToggleAction(view))
    }

    private class NotRustTestsFilterToggleAction(
        private val view: UsageView,
    ) : ToggleAction(
        "Exclude Usages in Rust Tests",
        "Hide usages inside Rust #[test] functions",
        AllIcons.Actions.Close
    ) {
        override fun isSelected(e: AnActionEvent): Boolean {
            val project = e.project ?: return false
            return isEnabled(project)
        }

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            val project = e.project ?: return
            setEnabled(project, state)
            project.messageBus.syncPublisher(UsageFilteringRuleProvider.RULES_CHANGED).run()
        }

        override fun update(e: AnActionEvent) {
            super.update(e)
            val visible = isRustUsageView(view, e)
            e.presentation.isVisible = visible
            e.presentation.isEnabled = visible
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    companion object {
        private const val ENABLED_KEY: String = "dev.plugin.filteroutrusttests.enabled"

        private fun isEnabled(project: Project): Boolean {
            return PropertiesComponent.getInstance(project).getBoolean(ENABLED_KEY, false)
        }

        private fun setEnabled(project: Project, enabled: Boolean) {
            PropertiesComponent.getInstance(project).setValue(ENABLED_KEY, enabled, false)
        }

        private fun isRustUsageView(view: UsageView, event: AnActionEvent): Boolean {
            val targets = event.getData(UsageView.USAGE_TARGETS_KEY)
            if (targets != null && targets.any { target ->
                    val element = (target as? PsiElementUsageTarget)?.element ?: return@any false
                    isRustElement(element.language.id)
                }) {
                return true
            }

            if (view.getUsages().any { usage ->
                    val element = (usage as? PsiElementUsage)?.element ?: return@any false
                    isRustElement(element.language.id)
                }) {
                return true
            }
            return false
        }

        private fun isRustElement(languageId: String): Boolean {
            return languageId == "Rust"
        }
    }
}
