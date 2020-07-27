import ast
import os
import pickle
from _ast import AST
from ast import iter_child_nodes
from typing import List, Optional

import asttokens

import config
from code_change_miner.changegraph.models import ChangeNode
from code_change_miner.pyflowgraph.models import Node
from common.models import AdjacencyList
from preprocessing.loaders import MinerOutputLoader, load_full_pattern_by_pattern_id


class PatternSubtreesExtractor:
    def __init__(self, pattern_nodes: List[ChangeNode]):
        self._pattern_nodes_before = [node for node in pattern_nodes
                                      if node.version == Node.Version.BEFORE_CHANGES]
        self._pattern_nodes_after = [node for node in pattern_nodes
                                     if node.version == Node.Version.AFTER_CHANGES]
        self._pattern_nodes_before_by_tokens = {(node.ast.first_token, node.ast.last_token): node
                                                for node in self._pattern_nodes_before}
        self._pattern_subtrees: List[AdjacencyList] = []

    def get_subtrees(self, ast_root: AST) -> List[AdjacencyList]:
        self._collect_subtrees(ast_root)
        return self._pattern_subtrees

    def get_changed_subtrees(self, ast_root: AST) -> List[AdjacencyList]:
        self._collect_subtrees(ast_root)
        changed_subtrees = []
        for subtree in self._pattern_subtrees:
            cg_nodes = [self._get_corresponding_cg_node(ast_node) for ast_node in subtree.keys()]
            all_changed = all(cg_node.mapped is None or cg_node.mapped.original_label != cg_node.original_label
                              for cg_node in cg_nodes)
            if all_changed:
                changed_subtrees.append(subtree)
        return changed_subtrees

    def _get_corresponding_cg_node(self, ast_node: AST) -> Optional[ChangeNode]:
        if not hasattr(ast_node, 'first_token') or not hasattr(ast_node, 'last_token'):
            return None
        return self._pattern_nodes_before_by_tokens.get((ast_node.first_token, ast_node.last_token))

    def _collect_subtrees(self, ast_current_node: AST, current_subtree: AdjacencyList = None):
        for ast_child_node in iter_child_nodes(ast_current_node):
            cg_child_node = self._get_corresponding_cg_node(ast_child_node)
            if cg_child_node is not None:
                if current_subtree is None:
                    current_subtree = AdjacencyList({ast_child_node: []})
                    self._pattern_subtrees.append(current_subtree)
                    self._collect_subtrees(ast_child_node, current_subtree)
                    current_subtree = None
                else:
                    current_subtree.setdefault(ast_current_node, []).append(ast_child_node)
                    self._collect_subtrees(ast_child_node, current_subtree)
            else:
                self._collect_subtrees(ast_child_node)

    @staticmethod
    def extract_and_save_pattern_subtrees():
        loader = MinerOutputLoader(config.MINER_OUTPUT_ROOT)
        for pattern_id in sorted(loader.patterns_path_by_id.keys()):
            print(f'Start pattern {pattern_id}')
            pattern = load_full_pattern_by_pattern_id(pattern_id)
            fragment_id = pattern.fragment_ids[0]  # it doesn't matter for a pattern which fragment to choose
            graph = pattern.fragments_graphs[fragment_id][0]  # there is only one graph for each fragment by default
            old_method, new_method = pattern.old_methods[fragment_id], pattern.new_methods[fragment_id]
            cg = pattern.change_graphs[fragment_id]

            # Extract nodes corresponding to pattern (it helps to drop out useless dependencies)
            pattern_edges = graph.get_edges()
            pattern_nodes_ids = set()
            for e in pattern_edges:
                pattern_nodes_ids.add(int(e.get_source()))
                pattern_nodes_ids.add(int(e.get_destination()))
            pattern_nodes = [node for node in cg.nodes if node.id in pattern_nodes_ids]

            # Extract only changed AST subtrees from pattern
            old_method_ast = ast.parse(old_method.get_source(), mode='exec')
            old_method_tokenized_ast = asttokens.ASTTokens(old_method.get_source(), tree=old_method_ast)
            extractor = PatternSubtreesExtractor(pattern_nodes)
            subtrees = extractor.get_changed_subtrees(old_method_tokenized_ast.tree)

            pattern_size = loader.patterns_path_by_id[pattern_id].split('/')[-2]
            path_to_subtrees = os.path.join(config.PATTERNS_SUBTREES_ROOT, str(pattern_size))
            if not os.path.exists(path_to_subtrees):
                os.makedirs(path_to_subtrees)
            path_to_subtrees = os.path.join(path_to_subtrees, str(pattern_id))
            if not os.path.exists(path_to_subtrees):
                os.makedirs(path_to_subtrees)
            path_to_subtrees = os.path.join(path_to_subtrees, f'fragment_{fragment_id}_subtrees.pickle')
            with open(path_to_subtrees, 'wb') as file:
                pickle.dump(subtrees, file)
            print(f'Saved subtrees for pattern {pattern_id}, fragment {fragment_id}')


if __name__ == '__main__':
    PatternSubtreesExtractor.extract_and_save_pattern_subtrees()
