import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.github.filteroutrusttests.NotRustTestsUsageFilteringRule
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
//import com.intellij.openapi.command.WriteCommandAction
//import com.intellij.openapi.editor.LogicalPosition
//import com.intellij.testFramework.PsiTestUtil
import com.intellij.openapi.fileEditor.TextEditorLocation
//import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
//import com.intellij.openapi.vfs.LocalFileSystem
//import com.intellij.openapi.vfs.VirtualFile
//import com.intellij.openapi.vfs.readText
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsNamedElement
import com.intellij.psi.search.searches.ReferencesSearch
//import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
//import com.jetbrains.rd.util.first
//import kotlinx.coroutines.runBlocking
//import org.junit.Ignore
//import org.junit.Test
//import org.rust.cargo.project.model.cargoProjects
//import org.rust.lang.core.psi.ext.RsTraitOrImpl
//import org.rust.openapiext.pathAsPath
import java.io.File

//import kotlin.io.path.Path
//import kotlin.io.path.createTempDirectory
//import kotlin.io.path.name
//import kotlin.io.path.pathString

data class Position(
    var line: Int = 0, var column: Int = 0
)

data class Expected(
    var position: Position = Position(), var visible: Boolean = false
)

data class TestCase(
    var source: String = "", var expected: List<Expected> = emptyList()
)

//data class Caret(
//    var path: String = "", var position: Position = Position()
//)
//
//data class TestFile(
//    var path: String = "", var expected: List<Expected> = arrayListOf()
//)

//data class ProjectTestCases(
//    var caret: Caret = Caret(), var files: List<TestFile> = arrayListOf()
//)

class NotRustTestsUsageFilteringRuleTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = findTestDataPath()

    private fun findTestDataPath(): String {
        System.getProperty("testDataPath")?.let { return it }

        var dir = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "src/test/testData")
            if (candidate.exists()) return candidate.absolutePath
            dir = dir.parentFile
        }

        return File("src/test/testData").absolutePath
    }

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

    private fun readUnitTestCases(relativeDir: String): Map<String, TestCase> {
        val mapper = TomlMapper()
        val root = File(testDataRoot, relativeDir)
        return root.walk().filter { it.isFile && it.extension == "toml" }
            .associate { it.name to mapper.readValue(it, TestCase::class.java) }
    }

    private val testDataRoot: String
        get() = getTestDataPath()

//    private fun readProjectsTestCases(relativeDir: String): Map<String, Pair<String, ProjectTestCases>> {
//        val root = File(testDataRoot, relativeDir)
//        val mapper = TomlMapper()
//
//        return root.listFiles { file -> file.isDirectory }?.associate { subDir ->
//            val testFile = subDir.resolve("expected.toml")
//            val relativeCaseDir = "$relativeDir/${subDir.name}"
//            (subDir.name to Pair(relativeCaseDir, mapper.readValue(testFile, ProjectTestCases::class.java)))
//        } ?: emptyMap()
//    }

    private fun collectUsages(element: PsiElement): Map<Position, Usage> =
        ReferencesSearch.search(element).findAll().associate { reference ->
            val usage = UsageInfo2UsageAdapter(UsageInfo(reference))
            val position = (usage.location as TextEditorLocation).position
            Position(position.line + 1, position.column + 1) to usage
        }

//    private fun collectProjectUsages(element: PsiElement): Map<String, Map<Position, Usage>> {
//        val map = mutableMapOf<String, MutableMap<Position, Usage>>()
//        val refs = ReferencesSearch.search(element).findAll()
//        for (ref in refs) {
//            val usage = UsageInfo2UsageAdapter(UsageInfo(ref))
//            val position = (usage.location as TextEditorLocation).position
//            val virtualFile = ref.element.containingFile.virtualFile ?: error("No file for usage")
//            val virtualPath = virtualFile.path.removePrefix("/src/project/")
//            map.getOrPut(virtualPath) { mutableMapOf() }[Position(position.line + 1, position.column + 1)] = usage
//        }
//        return map
//    }

    private fun checkExpectedUsages(
        rule: NotRustTestsUsageFilteringRule, usages: Map<Position, Usage>, expected: List<Expected>
    ): Result<Unit> = runCatching {
        require(expected.isNotEmpty()) { "No expectations provided." }
        require(expected.size == usages.size) {
            "Usage count mismatch: found ${usages.size}, expected ${expected.size}. Found usages at: ${usages.keys}"
        }

        expected.forEach { expected ->
            val usage = usages[expected.position]
                ?: error("Usage at ${expected.position.line}:${expected.position.column} expected but not found. Found usages: ${usages.keys}")

            check(expected.visible == rule.isVisible(usage)) {
                "Visibility mismatch at ${expected.position.line}:${expected.position.column}: expected visible=${expected.visible}"
            }
        }
    }

    private fun runUnitTestCase(name: String, testCase: TestCase): Result<Unit> {
        val caretString = "<caret>"
        val caretIndex = testCase.source.indexOf(caretString)
        if (caretIndex == -1) {
            error("Caret not found in test case source")
        }
        val file = myFixture.addFileToProject("$name.rs", testCase.source.trimIndent())
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val element = myFixture.file.findElementAt(myFixture.caretOffset) ?: error("No element at caret")
        val namedElement = element.parent
        val usages = collectUsages(namedElement)
        val rule = NotRustTestsUsageFilteringRule()
        return checkExpectedUsages(rule, usages, testCase.expected)
    }

//    private fun runProjectsTestCase(testCase: Pair<String, ProjectTestCases>): Result<Unit> = runCatching {
//        val (relativeDir, projectTestCases) = testCase
////        WriteCommandAction.runWriteCommandAction(project) {
////            myFixture.tempDirFixture.getFile(".")?.children?.forEach {
////                it.delete(null)
////            }
////            // TODO omit target and Cargo.lock
//            val projectDir = "$relativeDir/project"
////            myFixture.copyDirectoryToProject("$projectDir/src", "src")
////            myFixture.copyDirectoryToProject("$projectDir/tests", "tests")
////            myFixture.copyFileToProject("$projectDir/Cargo.toml", "Cargo.toml")
////        }
////
////        val root = myFixture.findFileInTempDir("")
////        fun printTree(file: VirtualFile, indent: String = "") {
////            println(indent + file.name)
////            if (file.isDirectory) {
////                file.children.forEach { printTree(it, "$indent  ") }
////            }
////        }
////        printTree(root!!)
////        val cargoToml = myFixture.findFileInTempDir("Cargo.toml")!!
////        runBlocking {
////            project.cargoProjects.attachCargoProject(cargoToml.toNioPath()).await()
////        }
////        IndexingTestUtil.waitUntilIndexesAreReady(project)
//////            val root = myFixture.findFileInTempDir("")
//////            if (root != null) {
//////                PsiTestUtil.addSourceRoot(module, root)
//////            }
//
//        val tempDir = File(createTempDirectory().pathString)
//
//        File("$testDataRoot/$projectDir")
//            .copyRecursively(tempDir, overwrite = true)
//
//        val vfsRoot = LocalFileSystem.getInstance()
//            .refreshAndFindFileByIoFile(tempDir)
//            ?: error("VFS root not found")
//
//        WriteCommandAction.runWriteCommandAction(project) {
//            PsiTestUtil.addContentRoot(module, vfsRoot)
//        }
//
//        val cargoToml = tempDir.resolve("Cargo.toml")
//
//        runBlocking {
//            project.cargoProjects.attachCargoProject(cargoToml.toPath()).await()
//        }
//
//        IndexingTestUtil.waitUntilIndexesAreReady(project)
//
//        val caretFilePath = projectTestCases.caret.path
//        val caretFile = myFixture.findFileInTempDir(caretFilePath)
//            ?: error("Caret file not found: $caretFilePath")
//        myFixture.configureFromExistingVirtualFile(caretFile)
//
//        val caretPos = projectTestCases.caret.position
//        val logicalPosition = LogicalPosition(caretPos.line - 1, caretPos.column - 1)
//        myFixture.editor.caretModel.moveToLogicalPosition(logicalPosition)
//
//        val element = myFixture.file.findElementAt(myFixture.caretOffset) ?: error("No element at caret")
//        val namedElement =
//            PsiTreeUtil.getParentOfType(element, RsTraitOrImpl::class.java) ?: PsiTreeUtil.getParentOfType(
//                element,
//                RsFunction::class.java
//            ) ?: PsiTreeUtil.getParentOfType(
//                element, RsNamedElement::class.java
//            ) ?: element
//        val projectUsages = collectProjectUsages(namedElement)
//        val rule = NotRustTestsUsageFilteringRule()
//
//        projectTestCases.files.forEach { case ->
//            val testPath = case.path
//            val testFile = myFixture.findFileInTempDir(testPath) ?: error("Test file not found: $testPath")
//            val source = testFile.readText()
//            val testCase = TestCase(source, case.expected)
//            val usages = projectUsages[testPath] ?: error("Usages not found for test file: $testPath")
//
//            val result = checkExpectedUsages(rule, usages, testCase.expected)
//            if (result.isFailure) {
//                return Result.failure(RuntimeException("Test case '${testPath}' failed: ${result.exceptionOrNull()?.message}"))
//            }
//        }
//    }

    private fun isRustPsiAvailable(): Boolean {
        val file = myFixture.configureByText("test.rs", "fn main() {}")
        return file::class.simpleName != "PsiPlainTextFileImpl"
    }

    fun testRustUnitTests() {
        val testCasesDir = "unit"
        val testCases = readUnitTestCases(testCasesDir)
        if (testCases.isEmpty()) {
            error("No test cases found in $testCasesDir")
        }
        testCases.forEach { (name, testCase) ->
            runUnitTestCase(name, testCase).onFailure { exception ->
                fail("Test case '$name' failed: ${exception.message}")
            }
        }
    }

    fun testRustIntegrationTests() {
        return // Cannot attach cargo to a VFS
//        val testCasesDir = "projects"
//        val testCases = arrayListOf(readProjectsTestCases(testCasesDir).first())
//        if (testCases.isEmpty()) {
//            error("No test cases found in $testCasesDir")
//        }
//        testCases.forEach { (name, testCase) ->
//            runProjectsTestCase(testCase).onFailure { exception ->
//                fail("Test case '$name' failed: ${exception.message}")
//            }
//        }
    }
}