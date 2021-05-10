package org.jetbrains.research.preprocessing.loaders

import com.github.gumtreediff.actions.ActionGenerator
import com.github.gumtreediff.actions.model.Action
import com.github.gumtreediff.matchers.Matchers
import com.intellij.openapi.project.Project
import org.jetbrains.research.common.gumtree.PyPsiGumTreeGenerator
import org.jetbrains.research.preprocessing.models.CodeChangeSample

class CachingEditActionsLoader private constructor(private val myProject: Project) {
    private val editActionsCache = hashMapOf<CodeChangeSample, List<Action>>()

    companion object {
        private var INSTANCE: CachingEditActionsLoader? = null

        fun getInstance(project: Project) =
            INSTANCE ?: CachingEditActionsLoader(project).also { INSTANCE = it }
    }

    fun loadEditActions(codeChangeSample: CodeChangeSample): List<Action> =
        if (editActionsCache.containsKey(codeChangeSample))
            editActionsCache[codeChangeSample]!!
        else {
            val psiBefore = CachingPsiLoader.getInstance(myProject).loadPsiFromSource(codeChangeSample.codeBefore)
            val psiAfter = CachingPsiLoader.getInstance(myProject).loadPsiFromSource(codeChangeSample.codeAfter)
            val srcGumtree = PyPsiGumTreeGenerator().generate(psiBefore).root
            val dstGumtree = PyPsiGumTreeGenerator().generate(psiAfter).root
            val matcher = Matchers.getInstance().getMatcher(srcGumtree, dstGumtree).also { it.match() }
            ActionGenerator(srcGumtree, dstGumtree, matcher.mappings).generate()
        }
}