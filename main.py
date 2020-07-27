import config
from localization.run import locate_pattern_by_subgraph, locate_pattern_by_subtree
from preprocessing.loaders import MinerOutputLoader, SubtreesLoader
from transforming import transform_method_using_pattern

if __name__ == '__main__':
    pattern_loader = MinerOutputLoader(config.MINER_OUTPUT_ROOT)
    patterns_graphs_paths = pattern_loader.get_all_pattern_fragments_graphs_paths()
    locate_pattern_by_subgraph(patterns_graphs_paths, 'tests/pattern_10_103.py')

    subtrees_loader = SubtreesLoader(config.PATTERNS_SUBTREES_ROOT)
    patterns_subtrees_paths = subtrees_loader.get_patterns_subtrees_paths()
    locate_pattern_by_subtree(patterns_subtrees_paths, 'tests/pattern_10_103.py')

    pattern_fragments_graphs_paths = pattern_loader.get_pattern_fragments_graphs_paths(pattern_id=103)
    transform_method_using_pattern(target_method_path='tests/pattern_10_103.py',
                                   fixed_method_path='tests/pattern_10_103_fixed.py',
                                   target_pattern_fragments_graphs_paths=pattern_fragments_graphs_paths)
