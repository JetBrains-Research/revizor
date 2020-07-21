from typing import List

from localization.subgraphs import SubgraphSeeker
from localization.subtrees import SubtreeSeeker


def locate_pattern_by_subtree(patterns_subtrees_paths: List[str], target_method_path):
    seeker = SubtreeSeeker(target_method_path)
    for pattern_subtree_path in patterns_subtrees_paths:
        found = seeker.find_isomorphic_subtree(pattern_subtree_path)
        if found is not None:
            print(f'Found suitable subtree: {pattern_subtree_path}')
            print(found)


def locate_pattern_by_subgraph(patterns_graphs_paths: List[str], target_method_path):
    seeker = SubgraphSeeker(target_method_path)
    for pattern_graph_path in patterns_graphs_paths:
        found = seeker.find_isomorphic_subgraphs(pattern_graph_path)
        if found is not None:
            print(f'Found suitable pattern: {pattern_graph_path}')
            print(found)
