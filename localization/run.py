from typing import List

from localization.subgraphs import SubgraphSeeker
from localization.subtrees import SubtreeSeeker
from models import Subtree


def locate_pattern_by_subtree(patterns_trees: List[Subtree], target_method_path):
    seeker = SubtreeSeeker(target_method_path)
    for pattern_tree in patterns_trees:
        found = seeker.find_isomorphic_subtree(pattern_tree)
        if found is not None:
            print(found)


def locate_pattern_by_subgraph(patterns_graphs_paths: List[str], target_method_path):
    seeker = SubgraphSeeker(target_method_path)
    for pattern_graph_path in patterns_graphs_paths:
        found = seeker.find_isomorphic_subgraphs(pattern_graph_path)
        if found is not None:
            print(pattern_graph_path)
