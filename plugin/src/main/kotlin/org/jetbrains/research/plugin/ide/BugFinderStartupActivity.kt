package org.jetbrains.research.plugin.ide

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.research.plugin.PatternsStorage

class BugFinderStartupActivity : StartupActivity {
    private val logger = Logger.getInstance(this::class.java)

    override fun runActivity(project: Project) {
        try {
            PatternsStorage.init(project)
        } catch (ex: Throwable) {
            logger.error("Failed to initialize PatternsStorage")
        }
    }
}