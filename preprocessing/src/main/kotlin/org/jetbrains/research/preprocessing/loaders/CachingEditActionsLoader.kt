package org.jetbrains.research.preprocessing.loaders

import com.github.gumtreediff.actions.ActionGenerator
import com.github.gumtreediff.matchers.Matchers
import com.intellij.openapi.project.Project
import org.jetbrains.research.common.gumtree.PyPsiGumTreeGenerator
import org.jetbrains.research.preprocessing.models.CodeChangeSample
import org.jetbrains.research.preprocessing.models.EditActions

class CachingEditActionsLoader private constructor(private val myProject: Project) {
    private val editActionsCache = hashMapOf<CodeChangeSample, EditActions>()

    companion object {
        private var INSTANCE: CachingEditActionsLoader? = null

        fun getInstance(project: Project) =
            INSTANCE ?: CachingEditActionsLoader(project).also { INSTANCE = it }
    }

    fun loadEditActions(codeChangeSample: CodeChangeSample): EditActions =
        if (editActionsCache.containsKey(codeChangeSample))
            editActionsCache[codeChangeSample]!!
        else {
            val psiBefore = CachingPsiLoader.getInstance(myProject).loadPsiFromSource(codeChangeSample.codeBefore)
            val psiAfter = CachingPsiLoader.getInstance(myProject).loadPsiFromSource(codeChangeSample.codeAfter)
            val srcGumtree = PyPsiGumTreeGenerator().generate(psiBefore).root
            val dstGumtree = PyPsiGumTreeGenerator().generate(psiAfter).root
            val matcher = Matchers.getInstance().getMatcher(srcGumtree, dstGumtree).also { it.match() }
            EditActions(ActionGenerator(srcGumtree, dstGumtree, matcher.mappings).generate())
                .also { editActionsCache[codeChangeSample] = it }
        }
}