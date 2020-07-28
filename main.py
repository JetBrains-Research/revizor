import config
from preprocessing.loaders import MinerOutputLoader
from transforming import transform_method_using_pattern

if __name__ == '__main__':
    pattern_loader = MinerOutputLoader(config.MINER_OUTPUT_ROOT)

    pattern_fragments_graphs_paths = pattern_loader.get_pattern_fragments_graphs_paths(pattern_id=103)
    transform_method_using_pattern(target_method_path='tests/pattern_10_103.py',
                                   fixed_method_path='tests/pattern_10_103_fixed.py',
                                   target_pattern_fragments_graphs_paths=pattern_fragments_graphs_paths)
