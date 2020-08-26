package org.jetbrains.research.plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.psi.PyFunction
import junit.framework.TestCase
import org.jetbrains.research.plugin.common.buildPyFlowGraphForMethod
import org.jetbrains.research.plugin.jgrapht.getStrictGraphIsomorphismInspector

/**
 *  A group of unit tests.
 *
 *  This class provides test cases for proving correctness of
 *  PyFlowGraph building algorithm in Kotlin using isomorphism checks
 *  between actual graph, that is built on PSI, and expected graph, that is
 *  built from Python AST via subprocess.
 */
class PyFlowGraphIsomorphismTest : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/test/resources/testData"

    private fun runTest(fileName: String) {
        val psiFile = myFixture.configureByFile(fileName)
        val node = psiFile.firstChild as PyFunction
        val expectedGraph = buildPyFlowGraphForMethod(node, builder = "python")
        val actualGraph = buildPyFlowGraphForMethod(node, builder = "kotlin")
        val isomorphismInspector = getStrictGraphIsomorphismInspector(expectedGraph, actualGraph)
        TestCase.assertTrue(isomorphismInspector.isomorphismExists())
    }

    fun `test empty function with pass statement`() = runTest("pass_statement.py")
    fun `test function definition with arguments`() = runTest("function_with_args.py")
    fun `test lambda declaration and call`() = runTest("lambda.py")

    fun `test arithmetic binary operations`() = runTest("arithmetic_binary_operations.py")
    fun `test boolean binary operations`() = runTest("boolean_binary_operations.py")
    fun `test comparison binary operations`() = runTest("comparison_binary_operations.py")
    fun `test unary operation`() = runTest("unary_operations.py")

    fun `test function call with arguments`() = runTest("function_call.py")
    fun `test nested attributes call`() = runTest("nested_attributes_call.py")

    fun `test subscript`() = runTest("subscript.py")
    fun `test slice`() = runTest("slice.py")
    fun `test subscript with slice`() = runTest("subscript_with_slice.py")

    fun `test simple assignment`() = runTest("simple_assignment.py")
    fun `test tuple pattern matching assignment`() = runTest("tuple_matching_assignment.py")
    fun `test pattern matching assignment with star expr`() = runTest("complex_matching_assignment.py")
    // FIXME: works incorrectly in the original approach
    // fun `test augmented assignments`() = runTest("aug_assign.py")

    fun `test list declaration`() = runTest("list_declaration.py")
    fun `test set declaration`() = runTest("set_declaration.py")
    fun `test tuple declaration`() = runTest("tuple_declaration.py")
    fun `test dict declaration`() = runTest("dict_declaration.py")

    fun `test for loop traversing list`() = runTest("for_traversing_list.py")
    fun `test for loop with list enumeration`() = runTest("for_enumeration.py")
    fun `test while loop with condition`() = runTest("while_loop.py")
    fun `test if-elif-else construction`() = runTest("if_condition.py")
    fun `test if with several elifs and else`() = runTest("many_elifs.py")
    fun `test try except`() = runTest("try_except.py")

    fun `test complicated for with continue statements`() = runTest("continue.py")
    fun `test complicated for with break statement`() = runTest("break.py")
}