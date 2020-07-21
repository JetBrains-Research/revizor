import pickle

import config
from preprocessing.loaders import PatternLoader, ChangeGraphLoader, RepositoryLoader, load_full_pattern_by_pattern_id

if __name__ == '__main__':
    """Extracting <pattern, change-graph> pairs for each pattern in Django repo
    """
    pattern_loader = PatternLoader(config.MINER_OUTPUT_ROOT)
    cg_loader = ChangeGraphLoader(config.CHANGE_GRAPHS_ROOT)
    repo_loader = RepositoryLoader(config.REPOSITORIES_ROOT)
    for pattern_id in range(1, 549):
        try:
            print(f'Start pattern {pattern_id}')
            pattern = load_full_pattern_by_pattern_id(pattern_id)
            with open(f'../data/patterns_django/full_pattern_{pattern_id}.pickle', 'wb') as file:
                pickle.dump(pattern, file)
            print(f'Saved pattern {pattern_id}')
        except Exception as e:
            print(f'Failed pattern {pattern_id}')
            print(e)
            continue

