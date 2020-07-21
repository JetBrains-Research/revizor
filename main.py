import config
from localization.run import locate_pattern_by_subgraph, locate_pattern_by_subtree
from preprocessing.loaders import MinerOutputLoader, SubtreesLoader

if __name__ == '__main__':
    pattern_loader = MinerOutputLoader(config.MINER_OUTPUT_ROOT)
    patterns_graphs_paths = pattern_loader.get_only_patterns_graphs_paths()
    locate_pattern_by_subgraph(patterns_graphs_paths, 'examples/103.py')

    # pattern_loader = SubtreesLoader(config.PATTERNS_SUBTREES_ROOT)
    # patterns_subtrees_paths = pattern_loader.get_patterns_subtrees_paths()
    # locate_pattern_by_subtree(patterns_subtrees_paths, 'examples/32.py')
