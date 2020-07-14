from changegraph.models import ChangeGraph, ChangeNode
from .loaders import ChangeGraphLoader, PatternLoader, RepositoryLoader

PATTERNS_OUTPUT_ROOT = '../../data/output_django/patterns_django'
CHANGE_GRAPHS_ROOT = '../../data/cgs_django'
REPOSITORIES_ROOT = '../../data/repos'

if __name__ == '__main__':
    pattern_loader = PatternLoader(PATTERNS_OUTPUT_ROOT)
    cg_loader = ChangeGraphLoader(CHANGE_GRAPHS_ROOT)
    repo_loader = RepositoryLoader(REPOSITORIES_ROOT)

    pattern_id = 340  # 340 - for debug fast search, 1 - for test
    details = pattern_loader.load_pattern_fragments_details(pattern_id)[0]
    cg: ChangeGraph = cg_loader.load_change_graph(repo_url=details['repo']['url'],
                                                  commit_hash=details['commit']['hash'],
                                                  old_file_path=details['files']['old']['path'],
                                                  old_method_full_name=details['methods']['old']['full_name'])
    old_method, new_method = repo_loader.load_methods_mapping(details)

    for node in cg.nodes:
        start, end = node.ast.first_token.startpos, node.ast.last_token.endpos
        if node.version == ChangeNode.Version.BEFORE_CHANGES:
            print(f' - {old_method.get_source()[start:end]}')
        else:
            print(f' + {new_method.get_source()[start:end]}')
