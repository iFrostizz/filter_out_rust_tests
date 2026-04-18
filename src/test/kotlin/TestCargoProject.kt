import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.RustcInfo
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.model.impl.CargoProjectImpl
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.UserEnabledFeatures
import org.rust.cargo.toolchain.tools.Cargo
import org.rust.cargo.toolchain.tools.Cargo.Companion.GeneratedFilesHolder
import org.rust.ide.newProject.openFiles
import java.nio.file.Path

class TestCargoProject(
    override val buildScriptEvaluationStatus: CargoProject.UpdateStatus,
    override val manifest: Path = Path.of("Cargo.toml"),
    override val presentableName: String,
    override val project: Project,
    override val rootDir: VirtualFile?,
    override val rustcInfo: RustcInfo?,
    override val rustcInfoStatus: CargoProject.UpdateStatus,
    override val rustcPrivateStatus: CargoProject.UpdateStatus,
    override val stdlibStatus: CargoProject.UpdateStatus,
    override val userEnabledFeatures: UserEnabledFeatures,
    override val workspace: CargoWorkspace?,
    override val workspaceRootDir: VirtualFile?,
    override val workspaceStatus: CargoProject.UpdateStatus
) : UserDataHolderBase(), CargoProject {
    fun getManifestPath(): Path = manifest

//    fun getFiles(): List<VirtualFile> {
//        return this.project.openFiles(
//            GeneratedFilesHolder(
//                manifest = this.project.cargoProjects.suggestManifests().first(),
//                sourceFiles = listOf()
//            )
//        )
//    }
}