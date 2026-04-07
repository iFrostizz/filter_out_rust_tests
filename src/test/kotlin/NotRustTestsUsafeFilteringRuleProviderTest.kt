import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.github.filteroutrusttests.NotRustTestsUsageFilteringRule
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.TextEditorLocation
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsNamedElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import java.io.File

data class Position(
    var line: Int = 0,
    var column: Int = 0
)

data class Expected(
    var position: Position = Position(),
    var visible: Boolean = false
)

data class TestCase(
    var source: String = "",
    var expected: List<Expected> = emptyList()
)

class NotRustTestsUsafeFilteringRuleProviderTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        assertTrue("Rust PSI is not available", isRustPsiAvailable())
        val pluginId = PluginId.getId("com.jetbrains.rust")
        val isRustInstalled = PluginManagerCore.isPluginInstalled(pluginId)
        assertTrue("Rust plugin is not installed", isRustInstalled)
        val plugin = PluginManagerCore.getPlugin(pluginId)
        assertNotNull("Rust plugin is not found", plugin)
        assertTrue("Rust plugin is not enabled", plugin?.isEnabled ?: false)
    }

    private fun readTestCases(dir: String): Map<String, TestCase> {
        val mapper = TomlMapper()
        return File(dir).walk()
            .filter { it.isFile && it.extension == "toml" }
            .associate { it.name to mapper.readValue(it, TestCase::class.java) }
    }

    private fun collectUsages(element: PsiElement): Map<Position, Usage> =
        ReferencesSearch.search(element).findAll().associate { reference ->
            val usage = UsageInfo2UsageAdapter(UsageInfo(reference))
            val position = (usage.location as TextEditorLocation).position
            Position(position.line + 1, position.column + 1) to usage
        }

    private fun runTestCase(name: String, testCase: TestCase): Result<Unit> = runCatching {
        val caretString = "<caret>"
        val caretIndex = testCase.source.indexOf(caretString)
        val finalSource = testCase.source.replace(caretString, "")
        val file = myFixture.addFileToProject("$name.rs", finalSource.trimIndent())
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        if (caretIndex != -1) {
            myFixture.editor.caretModel.moveToOffset(caretIndex)
        }
        val element = myFixture.file.findElementAt(myFixture.caretOffset) ?: error("No element at caret")
        val namedElement = PsiTreeUtil.getParentOfType(element, RsFunction::class.java)
            ?: PsiTreeUtil.getParentOfType(element, RsNamedElement::class.java)
            ?: element
        val usages = collectUsages(namedElement)
        val rule = NotRustTestsUsageFilteringRule()

        require(testCase.expected.isNotEmpty()) { "No expectations provided." }
        require(testCase.expected.size == usages.size) {
            "Usage count mismatch: found ${usages.size}, expected ${testCase.expected.size}. Found usages at: ${usages.keys}"
        }

        testCase.expected.forEach { expected ->
            val usage = usages[expected.position]
                ?: error("Usage at ${expected.position.line}:${expected.position.column} expected but not found. Found usages: ${usages.keys}")

            check(expected.visible == rule.isVisible(usage)) {
                "Visibility mismatch at ${expected.position.line}:${expected.position.column}: expected visible=${expected.visible}"
            }
        }
    }

    private fun isRustPsiAvailable(): Boolean {
        val file = myFixture.configureByText("test.rs", "fn main() {}")
        return file::class.simpleName != "PsiPlainTextFileImpl"
    }

    fun testRustUsages() {
        val testCasesDir = "src/test/testData"
        val testCases = readTestCases(testCasesDir)
        if (testCases.isEmpty()) {
            error("No test cases found in $testCasesDir")
        }
        testCases.forEach { (name, testCase) ->
            runTestCase(name, testCase).onFailure { exception ->
                fail("Test case '$name' failed: ${exception.message}")
            }
        }
    }
}