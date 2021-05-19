package org.jetbrains.research.preprocessing

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.SystemExitException
import com.xenomachina.argparser.default
import org.jetbrains.research.preprocessing.models.Pattern
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess


class PreprocessingRunner : ApplicationStarter {
    private lateinit var sourceDir: Path
    private lateinit var destDir: Path
    private var addDescription: Boolean = false

    private lateinit var myProject: Project
    private val logger = Logger.getInstance(this::class.java)

    override fun getCommandName(): String = "preprocessing"

    class PreprocessingRunnerArgs(parser: ArgParser) {
        val source by parser.storing(
            "-s", "--src",
            help = "path/to/patterns/mined/by/code-change-miner"
        )

        val destination by parser.storing(
            "-d", "--dest",
            help = "path/to/destination"
        )

        val desc by parser.flagging(
            "-a", "--addDescription",
            help = "add description to each pattern, will be shown in the IDE (optional)"
        ).default(false)
    }

    override fun main(args: Array<out String>) {
        try {
            ArgParser(args.drop(1).toTypedArray()).parseInto(::PreprocessingRunnerArgs).run {
                sourceDir = Paths.get(source)
                destDir = Paths.get(destination)
                addDescription = desc
            }
            sourceDir.toFile().listFiles()?.forEach { patternDir ->
                myProject = ProjectUtil.openOrImport(patternDir.toPath(), null, true)
                    ?: throw IllegalStateException("Can not import or create project")
                val pattern = Pattern(directory = patternDir.toPath(), project = myProject)
                if (addDescription) {
                    pattern.createDescription()
                }
                val targetDirectory = destDir.resolve(pattern.name)
                pattern.save(targetDirectory)
            }
        } catch (ex: SystemExitException) {
            logger.error(ex)
        } catch (ex: Exception) {
            logger.error(ex)
        } finally {
            exitProcess(0)
        }
    }
}