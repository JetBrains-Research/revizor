import config
from localization.run import locate_pattern_by_subgraph
from preprocessing.loaders import MinerOutputLoader

if __name__ == '__main__':
    pattern_loader = MinerOutputLoader(config.MINER_OUTPUT_ROOT)
    patterns_graphs_paths = pattern_loader.get_only_patterns_graphs_paths()
    locate_pattern_by_subgraph(patterns_graphs_paths, 'examples/103.py')
