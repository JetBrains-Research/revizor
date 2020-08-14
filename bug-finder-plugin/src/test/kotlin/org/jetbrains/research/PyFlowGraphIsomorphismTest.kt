package org.jetbrains.research

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.psi.PyFunction
import junit.framework.TestCase
import org.jetbrains.research.common.buildPyFlowGraphForMethod
import org.jetbrains.research.common.strictGraphIsomorphismExists

@ExperimentalStdlibApi
class PyFlowGraphIsomorphismTest : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/test/resources/testData"

    private fun runTest(fileName: String) {
        val psiFile = myFixture.configureByFile(fileName)
        val node = psiFile.firstChild as PyFunction
        val expectedGraph = buildPyFlowGraphForMethod(node, builder = "python")
        val actualGraph = buildPyFlowGraphForMethod(node, builder = "kotlin")
        TestCase.assertTrue(strictGraphIsomorphismExists(expectedGraph, actualGraph))
    }

    fun `test empty function with pass statement`() = runTest("pass_statement.py")
    fun `test function with arguments`() = runTest("function_with_args.py")

    fun `test arithmetic binary operations`() = runTest("arithmetic_binary_operations.py")
    fun `test boolean binary operations`() = runTest("boolean_binary_operations.py")
    fun `test comparison binary operations`() = runTest("comparison_binary_operations.py")
    fun `test unary operation`() = runTest("unary_operations.py")

    fun `test function call with arguments`() = runTest("function_call.py")
    fun `test nested attribute call`() = runTest("nested_attributes_call.py")

    fun `test subscript`() = runTest("subscript.py")
    fun `test slice`() = runTest("slice.py")
    fun `test subscript with slice`() = runTest("subscript_with_slice.py")
}