import os
import pickle
import json
from typing import Dict, List, Tuple

import pydot
import config

from pydriller import RepositoryMining

from changegraph.models import ChangeGraph
from code_change_miner.vcs.traverse import GitAnalyzer, Method
from models import PatternId, FragmentId, FragmentRepoDetails, Pattern


class MinerOutputLoader:
    def __init__(self, patterns_output_root):
        self.patterns_output_root = patterns_output_root
        self.patterns_path_by_id = {}
        pattern_sizes = os.listdir(self.patterns_output_root)
        for size in pattern_sizes:
            pattern_size_root = os.path.join(self.patterns_output_root, str(size))
            if not os.path.isdir(pattern_size_root):
                continue
            for pattern_id in os.listdir(pattern_size_root):
                if pattern_id.isdigit():
                    pattern_id_root = os.path.join(pattern_size_root, str(pattern_id))
                    self.patterns_path_by_id[int(pattern_id)] = pattern_id_root

    def load_pattern_fragments_details(self, pattern_id: PatternId) -> Dict[FragmentId, FragmentRepoDetails]:
        current_pattern_path = self.patterns_path_by_id[pattern_id]
        details_by_fragment_id = {}
        for filename in os.listdir(current_pattern_path):
            if filename.endswith('.json'):
                fragment_id = int(filename.split('-')[-1][:-5])
                with open(os.path.join(current_pattern_path, filename)) as json_file:
                    details_by_fragment_id[fragment_id] = FragmentRepoDetails(details=json.load(json_file))
        return details_by_fragment_id

    def load_pattern_fragments_graphs(self, pattern_id: PatternId) -> Dict[FragmentId, List[pydot.Graph]]:
        current_pattern_path = self.patterns_path_by_id[pattern_id]
        fragment_graphs = {}
        for filename in os.listdir(current_pattern_path):
            if filename.startswith('fragment') and filename.endswith('.dot'):
                fragment_id = int(filename.split('-')[-1][:-4])
                graphs = pydot.graph_from_dot_file(os.path.join(current_pattern_path, filename))
                fragment_graphs[fragment_id] = graphs
        return fragment_graphs

    def get_only_patterns_graphs_paths(self) -> List[str]:
        patterns_graphs_paths = []
        for pattern_id, current_pattern_path in self.patterns_path_by_id.items():
            for filename in os.listdir(current_pattern_path):
                if filename.startswith('fragment') and filename.endswith('.dot'):
                    dot_graph_path = os.path.join(current_pattern_path, filename)
                    patterns_graphs_paths.append(dot_graph_path)
                    break
        return patterns_graphs_paths

    def get_pattern_graphs_paths(self, pattern_id) -> List[str]:
        pattern_path = self.patterns_path_by_id[pattern_id]
        return [path for path in self.get_only_patterns_graphs_paths()
                if path.startswith(pattern_path + '/')]


class ChangeGraphLoader:
    def __init__(self, cgs_root):
        self.cgs_root = cgs_root

    def load_change_graph(self, details: FragmentRepoDetails) -> ChangeGraph:
        for file in os.listdir(self.cgs_root):
            with open(os.path.join(self.cgs_root, file), "rb") as pickle_file:
                graphs = pickle.load(pickle_file)
            for graph in graphs:
                cg = pickle.loads(graph)
                if (cg.repo_info.repo_url == details.repo_url
                        and cg.repo_info.commit_hash == details.commit_hash
                        and cg.repo_info.old_file_path == details.old_path
                        and cg.repo_info.old_method.full_name == details.old_method_full_name):
                    return cg
        raise FileNotFoundError


class RepositoryLoader:
    def __init__(self, repositories_root):
        self.repositories_root = repositories_root

    def load_methods_mapping(self, details: FragmentRepoDetails) -> Tuple[Method, Method]:
        repo = RepositoryMining(os.path.join(self.repositories_root, details.repo_name))
        for commit in repo.traverse_commits():
            if commit.hash == details.commit_hash:
                modifications = commit.modifications
                for mod in modifications:
                    if mod.old_path == details.old_path and mod.new_path == details.new_path:
                        old_methods_to_new = GitAnalyzer._get_methods_mapping(
                            GitAnalyzer._extract_methods(details.old_path, mod.source_code_before),
                            GitAnalyzer._extract_methods(details.new_path, mod.source_code))
                        for old_method, new_method in old_methods_to_new.items():
                            if (old_method.full_name == details.old_method_full_name
                                    and new_method.full_name == details.new_method_full_name):
                                return old_method, new_method
        raise FileNotFoundError


class SubtreesLoader:
    def __init__(self, subtrees_root):
        self.subtrees_root = subtrees_root

    def get_patterns_subtrees_paths(self) -> List[str]:
        paths = []
        pattern_sizes = os.listdir(self.subtrees_root)
        for size in pattern_sizes:
            pattern_size_root = os.path.join(self.subtrees_root, str(size))
            for pattern_id in os.listdir(pattern_size_root):
                pattern_id_root = os.path.join(pattern_size_root, str(pattern_id))
                for filename in os.listdir(pattern_id_root):
                    paths.append(os.path.join(pattern_id_root, filename))
        return paths


def load_full_pattern_by_pattern_id(pattern_id):
    try:
        with open(os.path.join(config.FULL_PATTERNS_ROOT, f'full_pattern_{pattern_id}.pickle'), 'rb') as file:
            pattern_dict = pickle.load(file)
            fragment_details = {}
            for fragment_id, details in pattern_dict['fragments_details'].items():
                fragment_details[fragment_id] = FragmentRepoDetails(details)
            pattern = Pattern(
                fragments_details=fragment_details,
                fragments_graphs=pattern_dict['fragments_graphs'],
                change_graphs=pattern_dict['change_graphs'],
                old_methods=pattern_dict['old_methods'],
                new_methods=pattern_dict['new_methods']
            )
    except FileNotFoundError:
        pattern_loader = MinerOutputLoader(config.MINER_OUTPUT_ROOT)
        cg_loader = ChangeGraphLoader(config.CHANGE_GRAPHS_ROOT)
        repo_loader = RepositoryLoader(config.REPOSITORIES_ROOT)
        fragments_details = pattern_loader.load_pattern_fragments_details(pattern_id)
        fragments_graphs = pattern_loader.load_pattern_fragments_graphs(pattern_id)
        change_graphs, old_methods, new_methods = {}, {}, {}
        for fragment_id, fragment_repo_details in fragments_details.items():
            change_graphs[fragment_id] = cg_loader.load_change_graph(fragment_repo_details)
            old_method, new_method = repo_loader.load_methods_mapping(fragment_repo_details)
            old_methods[fragment_id], new_methods[fragment_id] = old_method, new_method
            print(f'Loaded fragment {fragment_id} from pattern {pattern_id}')
        pattern = Pattern(fragments_details, fragments_graphs, change_graphs, old_methods, new_methods)

    return pattern
