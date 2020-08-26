package org.jetbrains.research.plugin

import com.google.gson.Gson
import java.nio.file.Path
import java.nio.file.Paths

/**
 * A singleton class for storing configuration settings.
 *
 * This class parses information from `src/main/resources/config.json`.
 * It is used only within `PyFlowGraphIsomorphismTest` class for calling
 * Python subprocess.
 */
object Config {
    val CODE_CHANGE_MINER_PATH: Path
    val TEMP_DIRECTORY_PATH: Path
    val PYTHON_EXECUTABLE_PATH: Path?

    init {
        val configJson = this::class.java.getResource("/config.json").readText()
        val config = Gson().fromJson(configJson, HashMap<String, String>()::class.java)
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