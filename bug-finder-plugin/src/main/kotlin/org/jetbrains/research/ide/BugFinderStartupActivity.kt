package org.jetbrains.research.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.research.common.Config


class BugFinderStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        Config.tempDirectory.mkdirs()
    }
}