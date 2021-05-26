package org.jetbrains.research.preprocessing

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.SystemExitException
import com.xenomachina.argparser.default
import org.jetbrains.research.preprocessing.models.Pattern
import java.io.File
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
            myProject = ProjectUtil.openOrImport(sourceDir, null, true)
            sourceDir.toFile().walk().forEach { patternDir ->
                if (patternDir.isDirectory && File(patternDir, "details.html").exists()) {
                    try {
                        val pattern = Pattern(directory = patternDir, project = myProject)
                        if (addDescription) {
                            pattern.createDescription()
                        }
                        val targetDirectory = destDir.resolve(pattern.name)
                        pattern.save(targetDirectory)
                        logger.warn("Successfully loaded pattern from $patternDir")
                    } catch (exception: Throwable) {
                        logger.warn("Failed to load pattern from $patternDir")
                        logger.error(exception)
                    }
                }
            }
        } catch (exception: SystemExitException) {
            logger.error(exception)
        } catch (exception: Exception) {
            logger.error(exception)
        } finally {
            exitProcess(0)
        }
    }
}