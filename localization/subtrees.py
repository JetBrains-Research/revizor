from _ast import AST
from ast import iter_child_nodes
from typing import Dict, List, Tuple

from preprocessing.traverse import Subtree


class SubtreeSeeker:
    def __init__(self, subtree: Subtree):
        self.subtree = subtree

    def find_ast_subtree(self, target_node: AST, pattern_node: AST):
        matched_subtree_nodes = self._get_matched_subtree_from_root(pattern_tree_root=pattern_node,
                                                                    target_tree_root=target_node,
                                                                    matched_subtree_nodes={})
        if matched_subtree_nodes is not None:
            return matched_subtree_nodes
        else:
            for child_node in iter_child_nodes(target_node):
                found_subtree_nodes = self.find_ast_subtree(pattern_node, child_node)
                if found_subtree_nodes is not None:
                    return found_subtree_nodes
            return None

    @staticmethod
    def _get_node_properties(node: AST):
        return type(node)

    def _are_nodes_equal(self, node_1: AST, node_2: AST):
        return self._get_node_properties(node_1) == self._get_node_properties(node_2)

    def _get_satisfiable_combination(self, match_constraints: Dict[AST, List[AST]]):
        return self.__sat_helper(match_constraints=list(match_constraints.items()), combination=[], depth=0)

    def __sat_helper(self, match_constraints: List[Tuple[AST, List[AST]]], combination: List[AST], depth: int):
        if depth < len(match_constraints):
            pattern_node, matched_target_nodes = match_constraints[depth]
            for target_node in matched_target_nodes:
                combination.append(target_node)
                if self.__sat_helper(match_constraints, combination, depth + 1) is None:
                    combination.pop()
                else:
                    return combination
            return None
        else:
            return combination if len(set(combination)) == len(match_constraints) else None

    def _get_matched_subtree_from_root(self, pattern_tree_root: AST,
                                       target_tree_root: AST,
                                       matched_subtree_nodes: Dict[AST, AST]):
        if self._are_nodes_equal(pattern_tree_root, target_tree_root):
            matched_children = {}
            matched_subtree_by_target_node = {}
            for pattern_node_child in self.subtree[pattern_tree_root]:
                for target_node_child in iter_child_nodes(target_tree_root):
                    child_matched_subtree_nodes = self._get_matched_subtree_from_root(pattern_node_child,
                                                                                      target_node_child,
                                                                                      matched_subtree_nodes={})
                    if child_matched_subtree_nodes is not None:
                        key = self._get_node_properties(pattern_node_child)
                        suitable_nodes = matched_children.setdefault(key, {}).setdefault(pattern_node_child, [])
                        suitable_nodes.append(target_node_child)
                        matched_subtree_by_target_node[target_node_child] = child_matched_subtree_nodes
            for pattern_child_node_property, suitable_target_nodes_by_pattern_node in matched_children.items():
                combination = self._get_satisfiable_combination(suitable_target_nodes_by_pattern_node)
                if combination is None:
                    return None
                else:
                    for pattern_node, target_node in zip(suitable_target_nodes_by_pattern_node.keys(), combination):
                        matched_subtree_nodes.update(matched_subtree_by_target_node[target_node])
                        matched_subtree_nodes[pattern_node] = target_node
            matched_subtree_nodes[pattern_tree_root] = target_tree_root
            return matched_subtree_nodes
        else:
            return None
