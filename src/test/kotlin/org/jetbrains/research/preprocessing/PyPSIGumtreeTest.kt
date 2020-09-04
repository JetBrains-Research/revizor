package org.jetbrains.research.preprocessing

import com.github.gumtreediff.matchers.Matchers
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.psi.PyElement


class PyPSIGumtreeTest : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/main/resources/patterns/2"

    fun `test gumtree matching on python psi`() {
        val psiFileBefore = myFixture.configureByFile("before.py")
        val psiFileAfter = myFixture.configureByFile("after.py")
        val gumtreeBefore = PyPSIGumTree(psiFileBefore.children[0] as PyElement)
        val gumtreeAfter = PyPSIGumTree(psiFileAfter.children[0] as PyElement)
        val matcher = Matchers.getInstance().getMatcher(gumtreeBefore, gumtreeAfter)
        matcher.match()
        UsefulTestCase.assertNotEmpty(matcher.mappingsAsSet)
    }
}