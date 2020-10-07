package org.jetbrains.research.plugin.ide

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import org.jetbrains.research.gumtree.PyElementTransformer
import org.jetbrains.research.plugin.PatternsStorage
import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex

class FixSuggestionsListPopupStep(
    problematicVertex: PatternSpecificVertex,
    private val holder: BugFinderInspection.PyMethodsAnalyzer.FoundProblemsHolder
) : BaseListPopupStep<String>(
    "Patterns", holder.patternsIdsByVertex[problematicVertex]?.toList() ?: listOf()
) {

    private var selectedPatternId: String = ""
    private val logger = Logger.getInstance(this::class.java)

    override fun getTextFor(patternId: String) = PatternsStorage.getPatternDescriptionById(patternId)

    override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
        selectedPatternId = selectedValue
        return super.onChosen(selectedValue, finalChoice)
    }

    override fun getFinalRunnable(): Runnable? {
        return Runnable { applyEditFromPattern(selectedPatternId) }
    }

    private fun applyEditFromPattern(patternId: String) {
        val actions = PatternsStorage.getPatternEditActionsById(patternId)
        val transformer = PyElementTransformer(PatternsStorage.project)
        for (vertex in holder.verticesByPatternId[patternId]!!) {
            val element = vertex.origin?.psi ?: continue
            for (action in actions) {
                try {
                    transformer.applyAction(element, action)
                } catch (ex: Throwable) {
                    logger.warn("Can't apply action $action to element $element")
                    logger.warn(ex)
                    continue
                }
            }
        }
    }
}