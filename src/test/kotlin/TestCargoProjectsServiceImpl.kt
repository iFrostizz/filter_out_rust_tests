import com.intellij.execution.RunManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.impl.CargoProjectImpl
import org.rust.cargo.project.model.impl.CargoProjectsServiceImpl
import org.rust.cargo.project.model.impl.CargoSyncTask
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.taskQueue
import java.io.File
import java.nio.file.Path
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.testFramework.LeakHunter
import kotlinx.coroutines.Deferred
import org.intellij.markdown.lexer.push
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.model.CargoProjectsService.CargoRefreshStatus
import org.rust.cargo.project.model.ContentEntryWrapper
import org.rust.cargo.project.model.setup
import org.rust.lang.RsFileType
import org.rust.stdext.AsyncValue
import org.rust.stdext.toResult
import java.util.concurrent.CompletableFuture

class TestCargoProjectsServiceImpl(project: Project, cs: CoroutineScope, val module: Module) :
    CargoProjectsServiceImpl(project, cs) {
    private val LOG = logger<TestCargoProjectsServiceImpl>()
    private val projects = AsyncValue<List<CargoProjectImpl>>(emptyList())

    private fun setupProjectRoots(project: Project, cargoProjects: List<CargoProject>) {
        invokeAndWaitIfNeeded {
            RunManager.getInstance(project)
            ProjectFileIndex.getInstance(project)

            runWriteAction {
                if (project.isDisposed) return@runWriteAction
                for (cargoProject in cargoProjects) {

                    val workspacePackages =
                        cargoProject.workspace?.packages.orEmpty().filter { it.origin == PackageOrigin.WORKSPACE }

                    for (pkg in workspacePackages) {
                        val pkgFile = pkg.contentRoot!!
                        ModuleRootModificationUtil.updateModel(module) { rootModel ->
                            val contentEntry = rootModel.contentEntries.singleOrNull() ?: return@updateModel
                            ContentEntryWrapper(contentEntry).setup(pkgFile)
                        }
                    }
                }
            }
        }
    }

    suspend fun doRefresh(
        project: Project, projects: List<CargoProjectImpl>
    ): List<CargoProjectImpl> {
        val result = CompletableDeferred<List<CargoProjectImpl>>()
        val syncTask = CargoSyncTask(project, projects, true, result)

        invokeLater {
            project.taskQueue.run(syncTask)
        }

        LOG.debug("Waiting for project refresh")

        val updatedProjects = result.await()
        setupProjectRoots(project, updatedProjects)
        return updatedProjects
    }

    protected fun modifyProjects2(
        updater: (List<CargoProjectImpl>) -> CompletableFuture<List<CargoProjectImpl>>
    ): CompletableFuture<List<CargoProjectImpl>> {
        val refreshStatusPublisher = project.messageBus.syncPublisher(CargoProjectsService.CARGO_PROJECTS_REFRESH_TOPIC)

        val wrappedUpdater = { projects: List<CargoProjectImpl> ->
            refreshStatusPublisher.onRefreshStarted()
            updater(projects)
        }

        return projects.updateAsync(wrappedUpdater).thenApply { projects ->
                invokeAndWaitIfNeeded {
                    val fileTypeManager = FileTypeManager.getInstance()
                    runWriteAction {
                        if (projects.isNotEmpty()) {
//                            checkRustVersion(projects)

                            // Android RenderScript (from Android plugin) files has the same extension (.rs) as Rust files.
                            // In some cases, IDEA determines `*.rs` files have RenderScript file type instead of Rust one
                            // that leads any code insight features don't work in Rust projects.
                            // See https://youtrack.jetbrains.com/issue/IDEA-237376
                            //
                            // It's a hack to provide proper mapping when we are sure that it's Rust project
                            fileTypeManager.associateExtension(RsFileType, RsFileType.defaultExtension)
                        }

//                        directoryIndex.resetIndex()
//                        // In unit tests roots change is done by the test framework in most cases
//                        runWithNonLightProject(project) {
//                            ProjectRootManagerEx.getInstanceEx(project)
//                                .makeRootsChange(EmptyRunnable.getInstance(), false, true)
//                        }
//                        project.messageBus.syncPublisher(CargoProjectsService.CARGO_PROJECTS_TOPIC)
//                            .cargoProjectsUpdated(this, projects)
                        initialized = true
                    }
                }
                projects
            }.handle { projects, err ->
//                val status = err?.toRefreshStatus() ?: CargoRefreshStatus.SUCCESS
//                refreshStatusPublisher.onRefreshFinished(status)
                projects
            }
    }

    fun attachTestCargoProject(manifest: Path): CompletableFuture<List<CargoProjectImpl>> {
//        LeakHunter.checkProjectLeak()
//        return modifyProjects {
        val cargoProjectImpl = CargoProjectImpl(manifest, this@TestCargoProjectsServiceImpl)
        return modifyProjects2({
            LOG.debug("Refreshing projects")
            println("Refreshing projects")
            runBlocking {
                val cargoProjects = doRefresh(project, it + cargoProjectImpl)
                println("Projects refreshed")
                CompletableFuture.completedFuture(cargoProjects)
            }
//            doRefresh(project, arrayListOf(CargoProjectImpl(manifest, this@TestCargoProjectsServiceImpl)))
//            doRefresh(project, it)
        })
//        return modifyProjects { doRefresh(project, it) }
    }

    fun createTestProject(manifest: Path): TestCargoProject {
        var cargoProject = CargoProjectImpl(
            manifest, this
        )
        LOG.debug("Creating test project")
        println("Creating test project")
        attachTestCargoProject(manifest).thenApply { refreshed ->
            refreshed.find { it.manifest == manifest }?.let {
                println("Found project")
                cargoProject = it
            }
            assert(cargoProject.workspaceStatus == CargoProject.UpdateStatus.UpToDate)
            println(cargoProject.workspaceRootDir?.path)
            File(cargoProject.workspaceRootDir?.path).listFiles {
                if (it.isDirectory) {
                    File(it.path).listFiles {
                        println(it)
                        true
                    }
                }
                true
            }
        }

        return TestCargoProject(
            buildScriptEvaluationStatus = cargoProject.buildScriptEvaluationStatus,
            manifest = cargoProject.manifest,
            presentableName = cargoProject.presentableName,
            project = cargoProject.project,
            rootDir = cargoProject.rootDir,
            rustcInfo = cargoProject.rustcInfo,
            rustcInfoStatus = cargoProject.rustcInfoStatus,
            rustcPrivateStatus = cargoProject.rustcPrivateStatus,
            stdlibStatus = cargoProject.stdlibStatus,
            userEnabledFeatures = cargoProject.userEnabledFeatures,
            workspace = cargoProject.workspace,
            workspaceRootDir = cargoProject.workspaceRootDir,
            workspaceStatus = cargoProject.workspaceStatus
        )
    }
}