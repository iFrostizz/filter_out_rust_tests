import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementsAtOffsetUp
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import org.junit.Assert.assertNotEquals
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import org.rust.lang.RsFileType
import org.rust.lang.core.psi.RsFunction


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
        val file = myFixture.configureByText("main.rs", testCase.source)
        assertFalse("file source is plain text", file is com.intellij.psi.impl.source.PsiPlainTextFileImpl)
        val offset = myFixture.caretOffset;
        assertNotEquals("<caret> not found", 0, offset);
        val leaf = myFixture.file.findElementAt(offset)
            ?: error("No element at caret")
        val function = leaf.parentOfType<RsFunction>()
            ?: error("Not inside a function")

        println(function.text) // should be "usage"

        // assert against expected
    }

    fun testRustUsages() {
        val testCases = readTestCases("src/test/testData")
        testCases.forEach { runTestCase(it) }
    }
}