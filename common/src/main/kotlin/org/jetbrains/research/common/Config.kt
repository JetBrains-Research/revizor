package org.jetbrains.research.common

import com.jetbrains.python.psi.LanguageLevel
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jetbrains.research.common.jgrapht.edges.PatternSpecificMultipleEdge
import org.jetbrains.research.common.jgrapht.vertices.PatternSpecificVertex
import org.jgrapht.Graph
import java.nio.file.Path
import java.nio.file.Paths

typealias PatternGraph = Graph<PatternSpecificVertex, PatternSpecificMultipleEdge>

/**
 * A singleton class for storing configuration settings.
 *
 * This class parses information from the `bug-finder/plugin/src/main/resources/config.json`.
 */
object Config {
    val CODE_CHANGE_MINER_PATH: Path
    val TEMP_DIRECTORY_PATH: Path
    val PYTHON_EXECUTABLE_PATH: Path?
    val LANGUAGE_LEVEL = LanguageLevel.PYTHON38

    init {
        val configJson = this::class.java.getResource("/config.json").readText()
        val config = Json.decodeFromString<HashMap<String, String>>(configJson)
        CODE_CHANGE_MINER_PATH = config["code_change_miner_path"]?.let { Paths.get(it) }
            ?: throw IllegalStateException("\"code_change_miner_path\" is not specified in config.json")
        TEMP_DIRECTORY_PATH = config["temp_directory_path"]?.let { Paths.get(it) }
            ?: throw IllegalStateException("\"temp_directory_path\" is not specified in config.json")
        TEMP_DIRECTORY_PATH.toFile().mkdirs()
        PYTHON_EXECUTABLE_PATH = config["python_executable_path"]?.let { Paths.get(it) }
            ?: System.getenv()["PYTHONPATH"]?.let { Paths.get(it) }
                    ?: throw IllegalStateException(
                "\"python_executable_path\" from config.json " +
                        "or environment variable \$PYTHONPATH are not specified"
            )
    }
}