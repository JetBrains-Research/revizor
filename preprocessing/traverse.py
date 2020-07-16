from ast import AST, iter_child_nodes
from typing import List, Dict
from dataclasses import dataclass

from changegraph.models import ChangeNode
from pyflowgraph.models import Node


@dataclass
class SubtreeWithContext:
    parent_label: AST
    subtree: Dict[AST, List[AST]]


class PatternSubtreesExtractor:
    def __init__(self, pattern_nodes: List[ChangeNode]):
        self._pattern_nodes_before = [node for node in pattern_nodes
                                      if node.version == Node.Version.BEFORE_CHANGES]
        self._pattern_nodes_after = [node for node in pattern_nodes
                                     if node.version == Node.Version.AFTER_CHANGES]
        self._pattern_nodes_by_tokens = {(node.ast.first_token, node.ast.last_token): node
                                         for node in self._pattern_nodes_before}
        self._pattern_subtrees: List[SubtreeWithContext] = []

    def get_subtrees(self, root):
        self._collect_subtrees(root)
        return self._pattern_subtrees

    def get_changed_subtrees(self, root):
        self._collect_subtrees(root)
        changed_subtrees_with_ctx = []
        for subtree_with_ctx in self._pattern_subtrees:
            cg_nodes = [self._get_corresponding_cg_node(ast_node) for ast_node in subtree_with_ctx.subtree.keys()]
            all_changed = all(cg_node.mapped is None or cg_node.mapped.original_label != cg_node.original_label
                              for cg_node in cg_nodes)
            if all_changed:
                changed_subtrees_with_ctx.append(subtree_with_ctx)
        return changed_subtrees_with_ctx

    def _get_corresponding_cg_node(self, ast_node):
        if not hasattr(ast_node, 'first_token') or not hasattr(ast_node, 'last_token'):
            return None
        return self._pattern_nodes_by_tokens.get((ast_node.first_token, ast_node.last_token))

    def _collect_subtrees(self, ast_current_node, current_subtree=None):
        for ast_child_node in iter_child_nodes(ast_current_node):
            cg_child_node = self._get_corresponding_cg_node(ast_child_node)
            if cg_child_node is not None:
                if current_subtree is None:
                    current_subtree = {ast_child_node: []}
                    self._pattern_subtrees.append(SubtreeWithContext(parent_label=ast_current_node,
                                                                     subtree=current_subtree))
                else:
                    current_subtree.setdefault(ast_current_node, []).append(ast_child_node)
                self._collect_subtrees(ast_child_node, current_subtree)
            else:
                self._collect_subtrees(ast_child_node)
