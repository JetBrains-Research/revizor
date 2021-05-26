package org.jetbrains.research.preprocessing.loaders

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.PyFunction
import java.io.File

class CachingPsiLoader private constructor(private val myProject: Project) {
    private val psiCache = HashMap<String, PyFunction>()

    companion object {
        private var INSTANCE: CachingPsiLoader? = null

        fun getInstance(project: Project) =
            INSTANCE ?: CachingPsiLoader(project).also { INSTANCE = it }
    }

    fun loadPsiFromFile(file: File): PyFunction =
        loadPsiFromSource(file.readText())

    fun loadPsiFromSource(src: String): PyFunction =
        if (psiCache.containsKey(src)) {
            psiCache[src]!!
        } else {
            val psi = PsiFileFactory.getInstance(myProject)
                .createFileFromText(PythonLanguage.getInstance(), src)
                .children.first() as PyFunction
            psiCache[src] = psi
            psi
        }
}