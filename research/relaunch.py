import os
import pickle

import pandas as pd

import config
from preprocessing.loaders import MinerOutputLoader

if __name__ == '__main__':
    patterns_df = pd.read_csv(os.path.join(config.DATA_ROOT, 'patterns.csv'))
    pattern_ids = patterns_df['First try ID (90 repo)'].values.astype(int)
    loader = MinerOutputLoader(config.MINER_OUTPUT_ROOT)

    hashes_by_repo_name = {}
    for pattern_id in pattern_ids:
        if pattern_id not in loader.pattern_ids:
            continue
        for fragment_id, details in loader.load_pattern_fragments_details(pattern_id).items():
            hashes_by_repo_name.setdefault(details.repo_name, set()).add(details.commit_hash)

    with open(os.path.join(config.DATA_ROOT, 'hashes_by_repo_name.pickle'), 'wb') as f:
        pickle.dump(hashes_by_repo_name, f, protocol=pickle.HIGHEST_PROTOCOL)
