import com.intellij.execution.RunManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.impl.CargoProjectImpl
import org.rust.cargo.project.model.impl.CargoProjectsServiceImpl
import org.rust.cargo.project.model.impl.CargoSyncTask
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.taskQueue
import java.io.File
import java.nio.file.Path

class TestCargoProjectsServiceImpl(project: Project, cs: CoroutineScope) : CargoProjectsServiceImpl(project, cs) {
    private fun setupProjectRoots(project: Project, cargoProjects: List<CargoProject>) {
        invokeAndWaitIfNeeded {
            // Initialize services that we use (probably indirectly) in write action below.
            // Otherwise, they can be initialized in write action that may lead to deadlock
            RunManager.getInstance(project)
            ProjectFileIndex.getInstance(project)

            runWriteAction {
                if (project.isDisposed) return@runWriteAction
                val projectRootManager = ProjectRootManagerEx.getInstanceEx(project)
                projectRootManager.mergeRootsChangesDuring {
                    for (cargoProject in cargoProjects) {
                        val workspaceRootDir = cargoProject.workspaceRootDir ?: continue
//                        workspaceRootDir.setupContentRoots(project) { contentRoot ->
//                            addExcludeFolder("${contentRoot.url}/target")
//                        }

//                        val workspacePackages = cargoProject.workspace?.packages
//                            .orEmpty()
//                            .filter { it.origin == PackageOrigin.WORKSPACE }

//                        println(workspacePackages.size)
//                        workspacePackages.forEach { println(it.name) }

//                        for (pkg in workspacePackages) {
//                            pkg.contentRoot?.setupContentRoots(project, ContentEntryWrapper::setup)
//                        }
                    }
                }
            }
        }
    }

    private suspend fun doRefresh(
        project: Project,
        projects: List<CargoProjectImpl>
    ): List<CargoProjectImpl> {
        val result = CompletableDeferred<List<CargoProjectImpl>>()
        val syncTask = CargoSyncTask(project, projects, true, result)

        invokeLater {
            project.taskQueue.run(syncTask)
        }

        val updatedProjects = result.await()
//        setupProjectRoots(project, updatedProjects)
        return updatedProjects
    }

    fun createTestProject(manifest: Path): TestCargoProject {
        var cargoProject = CargoProjectImpl(
            manifest,
            this
        );
        runBlocking {
            println("Creating test project")
            modifyProjects { projects ->
                val refreshed =
                    doRefresh(project, projects + CargoProjectImpl(manifest, this@TestCargoProjectsServiceImpl))
                refreshed.find { it.manifest == manifest }?.let {
                    cargoProject = it
                }
                assert(cargoProject.workspaceStatus == CargoProject.UpdateStatus.UpToDate)
                File(cargoProject.workspaceRootDir?.path).listFiles {
                    if (it.isDirectory) {
                        File(it.path).listFiles {
                            println(it.name)
                            true
                        }
                    }
                    true
                }
                refreshed
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