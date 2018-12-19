package de.phyrone.mavenplugin.radon

import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import java.io.File
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.ArtifactRequest
import org.eclipse.aether.resolution.ArtifactResult
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.util.filter.ScopeDependencyFilter

open class MavenResolver(
        val repositroys: List<RemoteRepository> = arrayListOf(
                RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build(),
                RemoteRepository.Builder("jcenter", "default", "https://jcenter.bintray.com/").build(),
                RemoteRepository.Builder("jitpack.io", "default", "https://jitpack.io/").build()
        ),
        repositoryDir: String = System.getProperty("user.home", "/") + "/.m2/repository/"
) {
    val localRepository = LocalRepository(repositoryDir)
    val locator = MavenRepositorySystemUtils.newServiceLocator().apply {
        addService(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
        addService(TransporterFactory::class.java, FileTransporterFactory::class.java)
        addService(TransporterFactory::class.java, HttpTransporterFactory::class.java)
    }
    val system = locator.getService(RepositorySystem::class.java)
    val mvnSession = MavenRepositorySystemUtils.newSession().also { session ->
        session.localRepositoryManager = system.newLocalRepositoryManager(session, localRepository)
    }

    /* -x resolve only the artifact jar without dependency's */
    fun resolve(artifact: Artifact): Array<File> {
        val ret = arrayListOf<File>()
        resolveArtifactDependencys(artifact).forEach {
            ret.add(resolveArtifactToFile(it.artifact))
        }
        return ret.toTypedArray()


    }

    fun resolveArtifactDependencys(artifact: Artifact): MutableList<ArtifactResult> {
        val dep = Dependency(artifact, "compile", false)

        val req = CollectRequest(dep, repositroys)
        val depReq = DependencyRequest(req, ScopeDependencyFilter( "test"))
        val result = system.resolveDependencies(mvnSession, depReq)
        return result.artifactResults

    }

    fun resolveArtifact(artifact: Artifact): ArtifactResult {
        val request = ArtifactRequest()
        request.artifact = artifact
        repositroys.forEach { repository -> request.addRepository(repository) }
        return system.resolveArtifact(mvnSession, request)
    }

    fun resolveArtifactToFile(artifact: Artifact): File {
        return resolveArtifact(artifact).artifact.file
    }
}