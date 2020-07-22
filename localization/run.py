from datetime import datetime
from typing import List

from tqdm import tqdm

import pyflowgraph
from localization.subgraphs import SubgraphSeeker
from localization.subtrees import SubtreeSeeker
from localization.utils import load_nx_graph_from_pyflowgraph, load_nx_graph_from_dot_file


def locate_pattern_by_subtree(patterns_subtrees_paths: List[str], target_method_path):
    seeker = SubtreeSeeker(target_method_path)
    max_size = 0
    result_mappings = {}
    for pattern_subtrees_path in patterns_subtrees_paths:
        found = seeker.find_isomorphic_subtree(pattern_subtrees_path)
        if found is not None:
            subtree_size = len(seeker.get_maximal_subtree(pattern_subtrees_path).nodes)
            if subtree_size >= max_size:
                max_size = subtree_size
                result_mappings.setdefault(max_size, []).append((found, pattern_subtrees_path))
    if result_mappings.get(max_size, None) is not None:
        print(f'There are {len(result_mappings[max_size])} suitable patterns, with maximal subtree size {max_size}:')
        for mapping, path in result_mappings[max_size]:
            print(f"Path to pattern's subtrees: {path}")
            print(f'Mapping: {mapping}')
    else:
        print('Nothing suitable found')


def locate_pattern_by_subgraph(patterns_graphs_paths: List[str], target_method_path):
    # Create and load corresponding graphs
    pfg = pyflowgraph.build_from_file(target_method_path)
    target_method_graph = load_nx_graph_from_pyflowgraph(pfg)
    seeker = SubgraphSeeker(target_method_graph)
    pattern_graph_by_path = {}
    print(f'Start loading patterns')
    for pattern_graph_path in tqdm(patterns_graphs_paths):
        pattern_graph_by_path[pattern_graph_path] = load_nx_graph_from_dot_file(pattern_graph_path)
    print(f'All {len(patterns_graphs_paths)} patterns are loaded\n')

    # Locate pattern subgraph
    max_size = 0
    result_mappings = {}
    start_time = datetime.now()
    for path, pattern_graph in pattern_graph_by_path.items():
        found = seeker.find_isomorphic_subgraphs(pattern_graph)
        if found is not None:
            pattern_size = len(pattern_graph.nodes)
            if pattern_size > max_size:
                max_size = pattern_size
                result_mappings.setdefault(max_size, []).append((found, path))
    end_time = datetime.now()

    # Print results
    print(f'Total time for pattern search: {end_time - start_time}')
    if result_mappings.get(max_size, None) is not None:
        print(f'There are {len(result_mappings[max_size])} suitable patterns, with maximal pattern size {max_size}:')
        for subgraph_generator, path in result_mappings[max_size]:
            print(f"Path to pattern's graph: {path}")
            print(f'Subgraph node ids mapping: {next(subgraph_generator)}')
    else:
        print('Nothing suitable found')
