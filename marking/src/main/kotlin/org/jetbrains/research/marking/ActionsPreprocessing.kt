package org.jetbrains.research.marking

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.research.plugin.Config

class ActionsPreprocessing : BasePlatformTestCase {
    fun test() {
        println(Config.LANGUAGE_LEVEL)
    }
}