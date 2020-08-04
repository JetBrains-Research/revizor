package org.jetbrains.research.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.research.common.BugFinderConfig
import org.jetbrains.research.common.PatternsState

class BugFinderStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        BugFinderConfig.tempDirectory.mkdirs()
        PatternsState
    }
}