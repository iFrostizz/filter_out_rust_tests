import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.impl.CargoProjectImpl
import org.rust.cargo.project.model.impl.CargoProjectsServiceImpl
import org.rust.cargo.project.model.impl.CargoSyncTask
import org.rust.taskQueue
import java.nio.file.Path

class TestCargoProjectsServiceImpl(project: Project, cs: CoroutineScope) : CargoProjectsServiceImpl(project, cs) {
    private fun doRefresh(
        project: Project,
        projects: List<CargoProjectImpl>
    ): List<CargoProjectImpl> {
        val result = CompletableDeferred<List<CargoProjectImpl>>()
        val syncTask = CargoSyncTask(project, projects, true, result)
        project.taskQueue.run(syncTask)
        return runBlocking {
            result.await()
        }
    }

    fun createTestProject(manifest: Path): TestCargoProject {
        val cargoProject = CargoProjectImpl(
            manifest,
            this
        );
        runBlocking {
            modifyProjects { projects ->
                doRefresh(project, projects + CargoProjectImpl(manifest, this as CargoProjectsServiceImpl))
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