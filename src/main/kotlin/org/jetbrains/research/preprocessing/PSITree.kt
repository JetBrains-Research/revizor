package org.jetbrains.research.preprocessing

import com.github.gumtreediff.tree.ITree
import com.github.gumtreediff.tree.TreeContext
import com.github.gumtreediff.tree.TreeUtils
import com.intellij.ide.impl.ProjectUtil
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyElement
import java.lang.IllegalStateException
import kotlin.collections.ArrayDeque


class PyPSITree(private val pyElement: PyElement) : ITree {
    private var id: Int = 0
    private var type: Int = 0
    private var label: String? = null
    private var metadata: MutableMap<String, Any?>

    private var children: MutableList<ITree>
    private var parent: ITree? = null

    private var hash: Int = 0
    private var size: Int = 0
    private var depth: Int = 0
    private var height: Int = 0
    private var length: Int = 0
    private var pos: Int = 0

    init {
        children = pyElement.children
            .filterIsInstance<PyElement>()
            .map { PyPSITree(it) }
            .toMutableList()
        metadata = mutableMapOf("origin" to pyElement)
    }

    override fun insertChild(t: ITree?, position: Int) {
        if (t != null) {
            children.add(index = position, element = t)
        }
    }

    override fun setId(id: Int) {
        this.id = id
    }

    override fun getId(): Int = id

    override fun hasLabel(): Boolean = label != null

    override fun setLabel(label: String?) {
        this.label = label
    }

    override fun getMetadata(key: String?): Any? = metadata[key]

    override fun getMetadata(): MutableIterator<MutableMap.MutableEntry<String, Any?>> = metadata.iterator()

    override fun refresh() {
        hash = 0
        size = 0
        depth = 0
        height = 0
    }

    override fun toTreeString(): String = TreeUtils.preOrder(this).joinToString(":")

    override fun setParent(parent: ITree?) {
        this.parent = parent
    }

    override fun getLength(): Int = length

    override fun preOrder(): MutableIterable<ITree> = TreeUtils.preOrder(this)

    override fun getHeight(): Int = height

    override fun getPos(): Int = pos

    override fun getChild(position: Int): ITree = children[position]

    override fun setDepth(depth: Int) {
        this.depth = depth
    }

    override fun setMetadata(key: String?, value: Any?): Any? {
        if (key != null) {
            this.metadata[key] = value
        }
        return value
    }

    override fun isRoot(): Boolean = parent != null

    override fun hasSameTypeAndLabel(t: ITree?): Boolean = this.type == t?.type && this.label == t.label

    override fun toShortString(): String? = "${this.label}(${this.type})" // FIXME

    override fun postOrder(): MutableIterable<ITree> = TreeUtils.postOrder(this)

    override fun toStaticHashString(): String {
        TODO("Not yet implemented")
    }

    override fun setChildren(children: MutableList<ITree>?) {
        this.children = children ?: arrayListOf()
    }

    override fun setParentAndUpdateChildren(parent: ITree?) {
        this.setParent(parent)
        parent?.addChild(this)
    }

    override fun positionInParent(): Int {
        this.parent?.children?.forEachIndexed { index, sibling ->
            if (sibling == this) {
                return index
            }
        }
        throw IllegalStateException("Node doesn't have parent or it is not registered as its child")
    }

    override fun getSize(): Int = size

    override fun isIsomorphicTo(tree: ITree?): Boolean =
        this.hash == tree?.hash && this.toStaticHashString() == tree.toStaticHashString()

    override fun hasSameType(t: ITree?): Boolean = this.type == t?.type

    override fun isLeaf(): Boolean = this.children.isEmpty()

    @ExperimentalStdlibApi
    override fun breadthFirst(): MutableIterable<ITree> {
        val queue = ArrayDeque<ITree>()
        val result = arrayListOf<ITree>()
        queue.addLast(this)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            result.add(current)
            current.children.forEach { queue.addLast(it) }
        }
        return result
    }

    override fun getDescendants(): MutableList<ITree> = this.preOrder().drop(1).toMutableList()

    override fun toPrettyString(ctx: TreeContext?): String = this.toTreeString() // FIXME

    override fun setPos(pos: Int) {
        this.pos = pos
    }

    override fun setHash(hash: Int) {
        this.hash = hash
    }

    override fun getChildPosition(child: ITree?): Int = this.children.indexOf(child)

    override fun getParent(): ITree? = parent

    override fun getChildren(): MutableList<ITree> = children

    override fun getLabel() = label

    override fun getParents(): MutableList<ITree> {
        val parents = arrayListOf<ITree>()
        if (this.parent != null) {
            parents.add(this.parent!!)
            parents.addAll(this.parent!!.parents)
        }
        return parents
    }

    override fun setType(type: Int) {
        this.type = type
    }

    override fun getType() = type

    override fun getDepth(): Int = depth

    override fun setSize(size: Int) {
        this.size = size
    }

    override fun setHeight(height: Int) {
        this.height = height
    }

    override fun setLength(length: Int) {
        this.length = length
    }

    override fun deepCopy(): ITree {
        val clone = PyPSITree(this.pyElement.copy() as PyElement)
        clone.id = this.id
        clone.type = this.type
        clone.label = this.label
        clone.metadata = this.metadata  // shareable
        clone.parent = this.parent
        clone.hash = this.hash
        clone.size = this.size
        clone.depth = this.depth
        clone.height = this.height
        clone.length = this.length
        clone.pos = this.pos
        return clone
    }

    override fun addChild(t: ITree?) {
        t?.let {
            this.children.add(it)
            it.parent = this
        }
    }

    override fun getHash(): Int = hash

    override fun getTrees(): MutableList<ITree> = this.preOrder().toMutableList()
}