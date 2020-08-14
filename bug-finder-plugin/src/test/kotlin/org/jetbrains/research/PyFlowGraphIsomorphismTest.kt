package org.jetbrains.research

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.psi.PyFunction
import junit.framework.TestCase
import org.jetbrains.research.common.buildPyFlowGraphForMethod
import org.jetbrains.research.common.strictGraphIsomorphismExists

@ExperimentalStdlibApi
class PyFlowGraphIsomorphismTest : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/test/resources/testData"

    fun `test empty function with pass statement`() {
        val psiFile = myFixture.configureByFile("pass_statement.py")
        val node = psiFile.firstChild as PyFunction
        val expected = buildPyFlowGraphForMethod(node, builder = "python")
        val actual = buildPyFlowGraphForMethod(node, builder = "kotlin")
        TestCase.assertTrue(strictGraphIsomorphismExists(expected, actual))
    }

}