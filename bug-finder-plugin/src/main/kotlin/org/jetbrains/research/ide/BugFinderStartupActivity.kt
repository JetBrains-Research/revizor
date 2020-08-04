package org.jetbrains.research.ide

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.research.PatternsStorage

class BugFinderStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        service<BugFinderConfigService>().state.tempDirectory.toFile().mkdirs()
        PatternsStorage
    }
}