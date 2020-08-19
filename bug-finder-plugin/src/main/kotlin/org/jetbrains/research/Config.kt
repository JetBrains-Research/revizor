package org.jetbrains.research

import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Path
import java.nio.file.Paths

object Config {
    val PATTERNS_STORAGE_PATH: Path
    val CODE_CHANGE_MINER_PATH: Path
    val TEMP_DIRECTORY_PATH: Path
    val PYTHON_EXECUTABLE_PATH: Path

    init {
        val stream = this::class.java.getResourceAsStream("/config.json")
        val configJson = InputStreamReader(stream).use { inputStreamReader ->
            BufferedReader(inputStreamReader).use { bufferedReader ->
                bufferedReader.readText()
            }
        }
        val config = Gson().fromJson(configJson, HashMap<String, String>()::class.java)
        PATTERNS_STORAGE_PATH = config["patterns_storage_path"]?.let { Paths.get(it) }
            ?: throw IllegalStateException("\"patterns_storage_path\" is not specified in config.json")
        CODE_CHANGE_MINER_PATH = config["code_change_miner_path"]?.let { Paths.get(it) }
            ?: throw IllegalStateException("\"code_change_miner_path\" is not specified in config.json")
        TEMP_DIRECTORY_PATH = config["temp_directory_path"]?.let { Paths.get(it) }
            ?: throw IllegalStateException("\"temp_directory_path\" is not specified in config.json")
        PYTHON_EXECUTABLE_PATH = System.getenv()["PYTHONPATH"]?.let { Paths.get(it) }
            ?: throw IllegalStateException("Environment variable \$PYTHONPATH is not specified")
    }
}