package de.phyrone.mavenplugin.radon

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import com.uchuhimo.konf.source.yaml.toYaml
import org.apache.maven.model.Dependency
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.util.*
import kotlin.collections.HashSet
import org.apache.maven.project.DefaultProjectBuildingRequest
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder
import org.apache.maven.artifact.resolver.filter.ExcludesArtifactFilter
import org.apache.maven.shared.dependency.graph.traversal.CollectingDependencyNodeVisitor


const val fancySpacerTimes = 64
const val fancySpacer = "*"
const val fancyName = " Radon-Plugin "
const val nameSpacers = fancySpacerTimes - fancyName.length
const val radonfilePlaceholder = "%radon"
const val radonConfigPlaceholder = "%config"

typealias AetherArtifact = org.eclipse.aether.artifact.DefaultArtifact

@Mojo(
        name = "obfuscate",
        defaultPhase = LifecyclePhase.PACKAGE,
        threadSafe = false
)
class RadonPlugin : AbstractMojo() {
    var reader = MavenXpp3Reader()


    @Parameter(defaultValue = "\${project}", required = true, readonly = true)
    private lateinit var project: MavenProject
    @Parameter(defaultValue = "\${session}", readonly = true, required = true)
    private lateinit var session: MavenSession
    @Parameter(defaultValue = "https://github.com/ItzSomebody/Radon/releases/download/1.0.4/Radon-Program.jar")
    private lateinit var radonurl: String
    @Parameter(defaultValue = "\${project.basedir}/radon/", required = true)
    private lateinit var radonFolder: String

    @Parameter(defaultValue = "java -jar $radonfilePlaceholder --config $radonConfigPlaceholder", required = true)
    private lateinit var radonCmd: String

    @Parameter(defaultValue = "none", required = false)
    private lateinit var radonConfig: String
    @Parameter(defaultValue = "false", required = false)
    private var keepTempConfig: Boolean = false
    @Parameter(defaultValue = "\${project.basedir}/target/\${project.build.finalName}.jar", required = true)
    lateinit var inputJar: String
    @Parameter(defaultValue = "\${project.basedir}/target/\${project.build.finalName}-Obfuscated.jar", required = true)
    lateinit var outputJar: String
    @Parameter(defaultValue = "false")
    private var skipAddSystemJARs: Boolean = false

    @Parameter(defaultValue = "false")
    private var skipCreateJava9UpRtJAR: Boolean = false
    @Parameter(defaultValue = "false")
    private var skip: Boolean = false
    @Parameter
    private var javaHome: String = System.getProperty("java.home")


    @Component(hint = "default")
    private lateinit var dependencyGraphBuilder: DependencyGraphBuilder

    override fun execute() {
        log.info(fancySpacer.repeat(fancySpacerTimes))
        log.info(fancySpacer.repeat(nameSpacers / 2) + fancyName + fancySpacer.repeat(nameSpacers / 2))
        log.info(fancySpacer.repeat(fancySpacerTimes))
        if (skip) {
            log.info("Skipped!")
            return
        }
        log.info("Project: " + project.groupId + ":" + project.artifactId + ":" + project.version)
        val rfile = File(radonFolder, "Radon-Program.jar")
        log.info("File: $radonFolder")
        /* Download Radon if not exists */
        if (!rfile.parentFile.exists())
            rfile.parentFile.mkdirs()
        if (!rfile.exists()) {
            val connection = URL(radonurl).openConnection()
            val inStream = connection.getInputStream()
            Files.copy(inStream, rfile.toPath())
        }
        /* Create Radon Config */
        val radonTmpConfig = File.createTempFile("RadonConfig", ".yml")
        var config = Config {
            cfgSpecs.forEach {
                addSpec(it)
            }
            ConfigurationSettings.values().forEach { cfg ->
                addSpec(RadonConfigValues.BlindKey(cfg.value))
            }
        }
        config = if (radonConfig.equals("none", true) || !File(radonConfig).exists()) {
            log.info("No Radon Config Found -> Use Default")
            config.from.yaml.string(defaultConfig.replace("%package%", (project.groupId + "." + project.artifactId).replace(".", "/")))
        } else {
            config.from.yaml.file(radonConfig)
        }
        log.debug("Resolve Pom Dependencies")
        val libs = getDependencyFiles()
        if (!skipAddSystemJARs) {
            libs.addAll(getSysLibrarays())
        }
        log.debug("Library's:")
        libs.forEach {
            log.debug("     - " + it.absolutePath)
        }

        val cfgLibs = config[RadonConfigValues.libs]
        libs.forEach { f ->
            cfgLibs.add(f.absolutePath)
        }
        config[RadonConfigValues.libs] = cfgLibs
        config[RadonConfigValues.inFile] = inputJar
        config[RadonConfigValues.outFile] = outputJar
        config.toYaml.toFile(radonTmpConfig)

        /* RunRadon */
        val commandLine = radonCmd
                .replace(radonConfigPlaceholder, radonTmpConfig.absolutePath, ignoreCase = true)
                .replace(radonfilePlaceholder, rfile.absolutePath)
        log.debug("RadonCommand: $commandLine")
        val process = Runtime.getRuntime().exec(commandLine, arrayOf(), rfile.parentFile)
        log.info("Radon Started!")
        val logListener = Thread({
            val output = Scanner(process.inputStream)
            while (output.hasNextLine()) {
                log.debug("RADON: " + output.nextLine())
            }
        }, "RadonLogListener")
        logListener.start()
        val result = process.waitFor()
        if (!keepTempConfig) {
            radonTmpConfig.deleteRecursively()
        }
        if (result != 0) {
            throw MojoFailureException("Radon Failed")
        }
        log.info("Radon Finish!")
    }

    private fun getSysLibrarays(): HashSet<File> {
        val ret = HashSet<File>()
        val rtJarFile = File("$javaHome/lib/rt.jar")
        val jceJarFile = File("$javaHome/lib/jce.jar")
        if (jceJarFile.exists()) {
            log.debug("Add JCE File to Library's (" + jceJarFile.absolutePath + ")")
            ret.add(jceJarFile)
        }
        if (rtJarFile.exists()) {
            log.debug("Add RT File to Library's (" + jceJarFile.absolutePath + ")")
            ret.add(rtJarFile)
        } else {
            if (!skipCreateJava9UpRtJAR) {
                val generatedRtFile = File(radonFolder, "RT.jar")
                if (!generatedRtFile.exists()) {
                    log.warn("rt.jar not found -> Extracting")
                    log.info("Thanks to Storyyeller (https://github.com/Storyyeller/jrt-extractor)")
                    log.debug("TempRtJarPath: " + generatedRtFile.absolutePath)
                    JRTExtractor.main(generatedRtFile, log)
                    ret.add(generatedRtFile)
                }
            }
        }
        return ret
    }

    private fun getDependencyFiles(): HashSet<File> {
        val ret = HashSet<File>()
        /*val repos = ArrayList<RemoteRepository>()
         project.repositories.forEach {
             repos.add(RemoteRepository.Builder(it.id, "default", it.url).build())
         }
         val resolver = MavenResolver(
                 repositroys = repos
         )
         log.debug("Dependency's")
         project.dependencies.forEach {
             log.debug("     - " + it.groupId + ":" + it.artifactId + ":" + it.version)
             val res = resolver.resolveArtifactDependencies(dependencyToArtifact(it))
             res.forEach { res ->
                 val resultFile = resolver.resolveArtifactToFile(res.artifact)
                 ret.add(resultFile)
             }
         }*/
        val artifactFilter = ExcludesArtifactFilter(listOf())
        val buildingRequest = DefaultProjectBuildingRequest(session.projectBuildingRequest)
        buildingRequest.project = project
        try {
            val depenGraphRootNode = dependencyGraphBuilder.buildDependencyGraph(buildingRequest, artifactFilter)
            val visitor = CollectingDependencyNodeVisitor()
            depenGraphRootNode.accept(visitor)
            val children = visitor.nodes

            log.debug("Dependencies :")
            for (node in children) {
                val artifact = node.artifact
                log.debug("     - ${artifact.groupId}:${artifact.artifactId}:${artifact.version}")
                ret.add(artifact.file)

            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ret
    }

    fun dependencyToArtifact(dependency: Dependency) = AetherArtifact(dependency.groupId, dependency.artifactId, dependency.classifier, dependency.type, dependency.version)

}

val cfgSpecs = arrayOf(
        RadonConfigValues
)

object RadonConfigValues : ConfigSpec() {
    val libs by optional(arrayListOf<String>(), "Libraries")
    val inFile by optional("", "Input")
    val outFile by optional("", "Output")

    class BlindKey(key: String) : ConfigSpec() {
        val blind by required<Any>(key)
    }
}

/**
 * An [Enum] containing all the allowed standalone configuration keys allowed.
 *
 * @author ItzSomebody
 */
enum class ConfigurationSettings private constructor(val value: String) {
    EXCLUSIONS("Exclusions"),
    STRING_ENCRYPTION("StringEncryption"),
    FLOW_OBFUSCATION("FlowObfuscation"),
    INVOKEDYNAMIC("InvokeDynamic"),
    LINE_NUMBERS("LineNumbers"),
    LOCAL_VARIABLES("LocalVariables"),
    NUMBER_OBFUSCATION("NumberObfuscation"),
    HIDE_CODE("HideCode"),
    CRASHER("Crasher"),
    EXPIRATION("Expiration"),
    WATERMARK("Watermarker"),
    OPTIMIZER("Optimizer"),
    SHRINKER("Shrinker"),
    SHUFFLER("Shuffler"),
    SOURCE_NAME("SourceName"),
    SOURCE_DEBUG("SourceDebug"),
    RENAMER("Renamer"),
    DICTIONARY("Dictionary"),
    TRASH_CLASSES("TrashClasses")
}

val defaultConfig = """
Renamer:
  Enabled: true
  Repackage: %package%
  AdaptResources:
    - META-INF/MANIFEST.MF
StringEncryption:
  Enabled: true
  Mode: Light
  Exclusions: []
HideCode: true
Dictionary: Alphanumeric
TrashClasses: 128
Exclusions: []
""".trimIndent()