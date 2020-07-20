from _ast import AST
from ast import iter_child_nodes
from typing import Dict, List, Tuple, Optional, TypeVar

from models import Subtree


class SubtreeSeeker:
    def __init__(self, subtree: Subtree):
        self.subtree = subtree

    def find_ast_subtree(self, target_node: AST, pattern_node: AST) -> Optional[Dict[AST, AST]]:
        matched_subtree_nodes = self._match_subtree_from_root(pattern_tree_root=pattern_node,
                                                              target_tree_root=target_node)
        if matched_subtree_nodes is not None:
            return matched_subtree_nodes
        else:
            for child_node in iter_child_nodes(target_node):
                found_subtree_nodes = self.find_ast_subtree(child_node, pattern_node)
                if found_subtree_nodes is not None:
                    return found_subtree_nodes
            return None

    NodePropertyType = TypeVar('NodePropertyType')

    def _get_node_properties(self, node: AST) -> NodePropertyType:
        node_properties = [node.__class__]
        for child_node in iter_child_nodes(node):
            node_properties.append(child_node.__class__)
        if hasattr(node, 'value'):
            if isinstance(node.value, AST):
                node_properties.append(self._get_node_properties(node.value))
            else:
                node_properties.append(node.value)
        if hasattr(node, 'id'):
            node_properties.append(node.id)
        if hasattr(node, 'attr'):
            node_properties.append(node.attr)
        return tuple(node_properties)

    def _are_nodes_equal(self, node_1: AST, node_2: AST) -> bool:
        return self._get_node_properties(node_1) == self._get_node_properties(node_2)

    def _get_satisfiable_combination(self, match_constraints: Dict[AST, List[AST]]) -> Optional[List[AST]]:
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

    def _match_subtree_from_root(self, pattern_tree_root: AST,
                                 target_tree_root: AST) -> Optional[Dict[AST, AST]]:
        if self._are_nodes_equal(pattern_tree_root, target_tree_root):
            matched_children: Dict[SubtreeSeeker.NodePropertyType, Dict[AST, List[AST]]] = {}
            matched_children_subtrees: Dict[AST, Dict[AST, AST]] = {}
            for pattern_node_child in self.subtree.get(pattern_tree_root, []):
                at_least_one_child_matched = False
                for target_node_child in iter_child_nodes(target_tree_root):
                    child_matched_subtree_nodes = self._match_subtree_from_root(pattern_node_child,
                                                                                target_node_child)
                    if child_matched_subtree_nodes is not None:
                        at_least_one_child_matched = True
                        key = self._get_node_properties(pattern_node_child)
                        suitable_nodes = matched_children.setdefault(key, {}).setdefault(pattern_node_child, [])
                        suitable_nodes.append(target_node_child)
                        matched_children_subtrees[target_node_child] = child_matched_subtree_nodes
                if not at_least_one_child_matched:
                    return None
            matched_subtree_nodes = {}
            for pattern_child_node_property, suitable_target_nodes_by_pattern_node in matched_children.items():
                combination = self._get_satisfiable_combination(suitable_target_nodes_by_pattern_node)
                if combination is None:
                    return None
                else:
                    for pattern_node, target_node in zip(suitable_target_nodes_by_pattern_node.keys(), combination):
                        matched_subtree_nodes.update(matched_children_subtrees[target_node])
            matched_subtree_nodes[pattern_tree_root] = target_tree_root
            return matched_subtree_nodes
        else:
            return None
