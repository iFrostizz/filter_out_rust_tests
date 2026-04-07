import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.PsiPlainTextFileImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.rust.ide.debugger.runconfig.getLoadedPlugins
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import org.rust.lang.RsFileType
import org.rust.lang.core.psi.RsCallExpr
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsFunction
import java.util.HashSet

class Position {
    var line: Int = 0
    var column: Int = 0
}

class TestCase {
    var source: String = ""
    var expected: Array<Position> = arrayOf()
}

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

    private fun readTestCases(dir: String): List<TestCase> {
        val fileSet: MutableSet<String> = HashSet()
        Files.newDirectoryStream(Paths.get(dir)).use { stream ->
            for (path in stream) {
                if (!Files.isDirectory(path)) {
                    fileSet.add(
                        path.toString()
                    )
                }
            }
        }

        return fileSet.map {
            val mapper = TomlMapper()
            val maybeTestCase: TestCase? = mapper.readValue(File(it), TestCase::class.java);
            val testCase: TestCase = maybeTestCase ?: throw RuntimeException("config is null");
            testCase
        }.toList();
    }

//    private fun collectUsages(element: PsiElement): List<Usage> {
//        val usageInfos = myFixture.findUsages(element)
//        return usageInfos.map { UsageInfo2UsageAdapter(it) }
//    }

    private fun runTestCase(testCase: TestCase) {
        myFixture.configureByText(RsFileType, /* language = Rust */ testCase.source.trimIndent())
//        assertEquals("Rust", myFixture.file.language.id)
        val file = myFixture.file
//        assertTrue("File is not Rust", file is RsFile)
        val element = file.findElementAt(myFixture.caretOffset)
        assertNotNull("No element found at caret", element)
        assertTrue("Element is not a Rust PSI element", element is RsFunction)
//        val fileClass = myFixture.file::class
//        println("file source: $fileClass")
//        assertTrue("file source is not Rust, found $fileClass", fileClass !is PsiPlainTextFileImpl)
////        val offset = myFixture.caretOffset;
//        val offset = myFixture.editor.caretModel.offset
////        println(offset);
//        assertNotEquals("<caret> not found", 0, offset);
//        val element = myFixture.file.findElementAt(offset)!!
////        val element = myFixture.elementAtCaret;
////        println(element.elementType);
////        println(element.text);
//        var current: PsiElement? = element
//        while (current != null) {
//            println("${current::class.simpleName} -> '${current.text}'")
//            current = current.parent
//        }
//        val function = element.parentOfType<RsCallExpr>() ?: error("Not inside a function")

//        println(function.text) // should be "usage"

        // assert against expected
    }

    fun testRustUsages() {
        val testCases = readTestCases("src/test/testData")
        testCases.forEach { runTestCase(it) }
    }

    private fun isRustPsiAvailable(): Boolean {
        val file = myFixture.configureByText("test.rs", "fn main() {}")
        return file::class.simpleName != "PsiPlainTextFileImpl"
    }
}