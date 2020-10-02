package org.jetbrains.research.gumtree

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyElement
import java.lang.reflect.Method
import java.lang.reflect.Modifier

sealed class PyElementChild
class EmptyChildContainer : PyElementChild()
class OneChildContainer(val element: PyElement) : PyElementChild()
class ManyChildrenContainer(val elements: Array<PyElement>) : PyElementChild()

internal fun extractChildrenByFieldName(element: PyElement): Map<String, PyElementChild> {
    val psiChildren: Set<PsiElement> = element.children.toSet()
    val methods: List<Method> = element.javaClass.methods
        .filter { Modifier.isPublic(it.modifiers) }
        .filter {
            PyElement::class.java.isAssignableFrom(it.returnType)
                    || Array<PyElement>::class.java.isAssignableFrom(it.returnType)
        }
    val childByMethodName = hashMapOf<String, PyElementChild>()
    for (method in methods) {
        val potentialChild = try {
            method.invoke(element)
        } catch (ex: Exception) {
            continue
        }
        if (potentialChild is PyElement && psiChildren.contains(potentialChild)) {
            childByMethodName[method.name] = OneChildContainer(potentialChild)
        } else if (potentialChild is Array<*>
            && potentialChild.all { it is PyElement && psiChildren.contains(it) }
        ) {
            childByMethodName[method.name] = ManyChildrenContainer(potentialChild as Array<PyElement>)
        } else {
            childByMethodName[method.name] = EmptyChildContainer()
        }
    }
    return childByMethodName
}
