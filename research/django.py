import json
import logging
import os
from typing import List

import pandas as pd
from tqdm import tqdm

import config
import pyflowgraph
from localization.subgraphs import SubgraphSeeker
from localization.utils import load_nx_graph_from_dot_file, load_nx_graph_from_pyflowgraph
from preprocessing.loaders import MinerOutputLoader
from vcs.traverse import GitAnalyzer, Method


def get_all_python_methods(repository_root: str) -> List[Method]:
    methods = []
    for root, dirs, files in os.walk(repository_root):
        for file in files:
            if file.endswith('.py'):
                path_to_python_file = os.path.join(root, file)
                with open(path_to_python_file, 'r') as f:
                    src = f.read()
                current_methods: List[Method] = GitAnalyzer._extract_methods(path_to_python_file, src)
                methods.extend(current_methods)
    return methods


if __name__ == '__main__':
    logger = logging.getLogger('logger')
    file_handler = logging.FileHandler("finder.log")
    logger.addHandler(file_handler)
    console_handler = logging.StreamHandler()
    logger.addHandler(console_handler)
    logger.setLevel('INFO')

    loader = MinerOutputLoader(config.MINER_OUTPUT_ROOT)
    interesting_df = pd.read_csv(os.path.join(config.MINER_OUTPUT_ROOT, 'interesting_patterns.csv'))
    pattern_graph_by_path = {}
    for interesting_pattern_id in tqdm(interesting_df['Pattern ID'].values):
        pattern_graph_path = loader.get_pattern_graphs_paths(interesting_pattern_id)[0]
        pattern_graph_by_path[pattern_graph_path] = load_nx_graph_from_dot_file(pattern_graph_path)

    results = {}
    all_methods = get_all_python_methods(os.path.join(config.REPOSITORIES_ROOT, 'django'))
    for method in tqdm(all_methods):
        pfg = pyflowgraph.build_from_source(method.get_source())
        method_graph = load_nx_graph_from_pyflowgraph(pfg)
        seeker = SubgraphSeeker(method_graph)
        found_subgraph_mappings = []

        for path, pattern_graph in pattern_graph_by_path.items():
            found = seeker.find_isomorphic_subgraphs(pattern_graph)
            if found is not None:
                pattern_size = len(pattern_graph.nodes)
                found_subgraph_mappings.append((found, path))

        if found_subgraph_mappings:
            logger.info('')
            logger.info(f'Success! For method <{method.full_name}> from file:')
            logger.info(f'{method.file_path}')
            logger.info(f'There are {len(found_subgraph_mappings)} suitable patterns:')
            for subgraph_generator, path in found_subgraph_mappings:
                subgraph = next(subgraph_generator, None)
                logger.info(f"  Path to pattern's graph: {path}")
                logger.info(f'  Subgraph node ids mapping: {subgraph}')
                current_result = results.setdefault(method.file_path, {}).setdefault(method.full_name, {})
                current_result['Pattern path'] = path
                current_result['Subgraphs mapped nodes'] = subgraph
            logger.info('')

    with open('results.json', 'w') as f:
        f.write(json.dumps(results, indent=4))
