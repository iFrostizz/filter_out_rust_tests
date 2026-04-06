import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.PsiPlainTextFileImpl
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import org.junit.Assert.assertNotEquals
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import org.rust.lang.RsFileType
import org.rust.lang.core.psi.RsCallExpr

class Position {
    var line: Int = 0
    var column: Int = 0
}

class TestCase {
    var source: String = ""
    var expected: Array<Position> = arrayOf()
}

class NotRustTestsUsafeFilteringRuleProviderTest : BasePlatformTestCase() {
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

    private fun collectUsages(element: PsiElement): List<Usage> {
        val usageInfos = myFixture.findUsages(element)
        return usageInfos.map { UsageInfo2UsageAdapter(it) }
    }

    private fun runTestCase(testCase: TestCase) {
        myFixture.configureByText(RsFileType, /* language = Rust */ testCase.source.trimIndent())
        val fileClass = myFixture.file::class
        assertFalse("file source is not Rust, found $fileClass", fileClass is PsiPlainTextFileImpl)
        val offset = myFixture.caretOffset;
//        println(offset);
        assertNotEquals("<caret> not found", 0, offset);
        val element = myFixture.file.findElementAt(offset)!!
//        println(element.elementType);
//        println(element.text);
        var current: PsiElement? = element
        while (current != null) {
            println("${current::class.simpleName} -> '${current.text}'")
            current = current.parent
        }
//        val function = element.parentOfType<RsCallExpr>() ?: error("Not inside a function")

//        println(function.text) // should be "usage"

        // assert against expected
    }

    fun testRustUsages() {
        val rustPlugin = PluginManagerCore.getPlugin(PluginId.getId("com.jetbrains.rust"))
        println("Rust plugin loaded: ${rustPlugin?.isEnabled}")

        val testCases = readTestCases("src/test/testData")
        testCases.forEach { runTestCase(it) }
    }
}