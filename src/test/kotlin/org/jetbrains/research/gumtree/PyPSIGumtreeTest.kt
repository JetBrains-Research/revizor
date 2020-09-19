package org.jetbrains.research.gumtree

import com.github.gumtreediff.actions.ActionGenerator
import com.github.gumtreediff.matchers.Matchers
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.psi.PyElement


class PyPSIGumtreeTest : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/main/resources/patterns/2"

    fun `test gumtree matching on python psi`() {
        val rootNodeBefore = myFixture.configureByFile("before.py").children.first() as PyElement
        val rootNodeAfter = myFixture.configureByFile("after.py").children.first() as PyElement
        val src = PyPsiGumTreeGenerator().generate(rootNodeBefore).root
        val dst = PyPsiGumTreeGenerator().generate(rootNodeAfter).root
        val matcher = Matchers.getInstance().getMatcher(src, dst)
        matcher.match()
        val mappings = matcher.mappings
        val generator = ActionGenerator(src, dst, mappings)
        val actions = generator.generate()
        UsefulTestCase.assertSize(2, actions)
        val transformer = PyPsiGumTreeTransformer()
        transformer.applyAction(actions[0].node as PyPsiGumTree, actions[0])
        print(src)
    }
}