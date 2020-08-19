package org.jetbrains.research.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.research.Config
import org.jetbrains.research.PatternsStorage

class BugFinderStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        Config
        PatternsStorage
        Config.TEMP_DIRECTORY_PATH.toFile().mkdirs()
    }
}