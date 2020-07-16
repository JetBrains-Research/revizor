import os
import pickle
import json
import pydot
import config

from pydriller import RepositoryMining
from vcs.traverse import GitAnalyzer


class PatternLoader:
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

    def load_pattern_fragments_details(self, pattern_id: int):
        current_pattern_path = self.patterns_path_by_id[pattern_id]
        details = {}
        for filename in os.listdir(current_pattern_path):
            if filename.endswith('.json'):
                fragment_id = int(filename.split('-')[-1][:-5])
                with open(os.path.join(current_pattern_path, filename)) as json_file:
                    details[fragment_id] = json.load(json_file)
        return details

    def load_pattern_fragments_graphs(self, pattern_id: int):
        current_pattern_path = self.patterns_path_by_id[pattern_id]
        fragment_graphs = {}
        for filename in os.listdir(current_pattern_path):
            if filename.startswith('fragment') and filename.endswith('.dot'):
                fragment_id = int(filename.split('-')[-1][:-4])
                graph = pydot.graph_from_dot_file(os.path.join(current_pattern_path, filename))
                fragment_graphs[fragment_id] = graph
        return fragment_graphs


class ChangeGraphLoader:
    def __init__(self, cgs_root):
        self.cgs_root = cgs_root

    def load_change_graph(self, repo_url, commit_hash, old_file_path, old_method_full_name):
        for file in os.listdir(self.cgs_root):
            with open(os.path.join(self.cgs_root, file), "rb") as pickle_file:
                graphs = pickle.load(pickle_file)
            for graph in graphs:
                cg = pickle.loads(graph)
                if (cg.repo_info.repo_url == repo_url
                        and cg.repo_info.commit_hash == commit_hash
                        and cg.repo_info.old_file_path == old_file_path
                        and cg.repo_info.old_method.full_name == old_method_full_name):
                    return cg
        raise FileNotFoundError


class RepositoryLoader:
    def __init__(self, repositories_root):
        self.repositories_root = repositories_root

    def load_methods_mapping(self, details):
        repo_name = details['repo']['name']
        commit_hash = details['commit']['hash']
        old_path = details['files']['old']['path']
        new_path = details['files']['new']['path']
        old_method_full_name = details['methods']['old']['full_name']
        new_method_full_name = details['methods']['new']['full_name']

        repo = RepositoryMining(os.path.join(self.repositories_root, repo_name))
        for commit in repo.traverse_commits():
            if commit.hash == commit_hash:
                modifications = commit.modifications
                for mod in modifications:
                    if mod.old_path == old_path and mod.new_path == new_path:
                        old_methods_to_new = GitAnalyzer._get_methods_mapping(
                            GitAnalyzer._extract_methods(old_path, mod.source_code_before),
                            GitAnalyzer._extract_methods(new_path, mod.source_code))
                        for old_method, new_method in old_methods_to_new.items():
                            if (old_method.full_name == old_method_full_name
                                    and new_method.full_name == new_method_full_name):
                                return old_method, new_method
        raise FileNotFoundError


def load_pattern_by_pattern_id(pattern_id):
    pattern_loader = PatternLoader(config.PATTERNS_OUTPUT_ROOT)
    cg_loader = ChangeGraphLoader(config.CHANGE_GRAPHS_ROOT)
    repo_loader = RepositoryLoader(config.REPOSITORIES_ROOT)

    pattern = {'fragments_details': pattern_loader.load_pattern_fragments_details(pattern_id),
               'fragments_graphs': pattern_loader.load_pattern_fragments_graphs(pattern_id),
               'change_graphs': {},
               'old_methods': {},
               'new_methods': {}
               }
    for fragment_id, fragment_repo_info in pattern['fragments_details'].items():
        pattern['change_graphs'][fragment_id] = cg_loader.load_change_graph(
            repo_url=fragment_repo_info['repo']['url'],
            commit_hash=fragment_repo_info['commit']['hash'],
            old_file_path=fragment_repo_info['files']['old']['path'],
            old_method_full_name=fragment_repo_info['methods']['old']['full_name'])
        old_method, new_method = repo_loader.load_methods_mapping(fragment_repo_info)
        pattern['old_methods'][fragment_id], pattern['new_methods'][fragment_id] = old_method, new_method
        print(f'Loaded fragment {fragment_id} from pattern {pattern_id}')

    return pattern
