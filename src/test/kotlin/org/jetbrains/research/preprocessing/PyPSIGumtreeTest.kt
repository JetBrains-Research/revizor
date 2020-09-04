package org.jetbrains.research.preprocessing

import com.github.gumtreediff.matchers.Matchers
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.psi.PyElement


class PyPSIGumtreeTest : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/main/resources/patterns/2"

    fun `test gumtree matching on python psi`() {
        val rootNodeBefore = myFixture.configureByFile("before.py").children.first() as PyElement
        val rootNodeAfter = myFixture.configureByFile("after.py").children.first() as PyElement
        val gumtreeBefore = PyPSIGumTreeGenerator().generate(rootNodeBefore)
        val gumtreeAfter = PyPSIGumTreeGenerator().generate(rootNodeAfter)
        val matcher = Matchers.getInstance().getMatcher(gumtreeBefore.root, gumtreeAfter.root)
        matcher.match()
        UsefulTestCase.assertSize(7, matcher.mappingsAsSet)
    }
}