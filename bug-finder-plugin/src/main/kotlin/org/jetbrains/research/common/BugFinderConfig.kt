package org.jetbrains.research.common

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

object BugFinderConfig {
    var patternsOutputPath: Path = Paths.get("/home/oleg/prog/jetbrains/data/relaunch_output")
    var codeChangeMinerPath: Path = Paths.get("/home/oleg/prog/jetbrains/bug-finder/code_change_miner")
    var pythonExecutablePath: Path = Paths.get("/home/oleg/miniconda3/envs/bug-finder/bin/python")
    var tempDirectory: File = File("/home/oleg/.temp/")
}
