import os
import pickle
import json

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
                pattern_id_root = os.path.join(pattern_size_root, str(pattern_id))
                self.patterns_path_by_id[pattern_id] = pattern_id_root

    def load_pattern_fragments_details(self, pattern_id: int):
        current_pattern_path = self.patterns_path_by_id[str(pattern_id)]
        details = []
        for filename in os.listdir(current_pattern_path):
            if filename.endswith('.json'):
                with open(os.path.join(current_pattern_path, filename)) as json_file:
                    details.append(json.load(json_file))
        return details


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
                        and cg.repo_info.old_method.full_name == old_method_full_name):  # TODO: use dict hash
                    return cg
        raise FileNotFoundError


class RepositoryLoader:
    def __init__(self, repositories_root):
        self.repositories_root = repositories_root

    def load_methods_mapping(self, repo_info_details):
        repo_name = repo_info_details['repo']['name']
        commit_hash = repo_info_details['commit']['hash']
        old_path = repo_info_details['files']['old']['path']
        new_path = repo_info_details['files']['new']['path']
        old_method_full_name = repo_info_details['methods']['old']['full_name']
        new_method_full_name = repo_info_details['methods']['new']['full_name']

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
