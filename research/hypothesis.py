import ast
import asttokens

import config
from preprocessing.loaders import PatternLoader, ChangeGraphLoader, RepositoryLoader
from preprocessing.traverse import PatternSubtreesExtractor

if __name__ == '__main__':
    """Checking how many changed disjoint AST subtrees are 
    usually stored in the graph of each pattern in Django repo
    """
    pattern_loader = PatternLoader(config.PATTERNS_OUTPUT_ROOT)
    cg_loader = ChangeGraphLoader(config.CHANGE_GRAPHS_ROOT)
    repo_loader = RepositoryLoader(config.REPOSITORIES_ROOT)
    result = {}
    for pattern_id in range(1, 549):
        try:
            print(f'Start pattern {pattern_id}')
            pattern = {'fragments_details': pattern_loader.load_pattern_fragments_details(pattern_id),
                       'fragments_graphs': pattern_loader.load_pattern_fragments_graphs(pattern_id),
                       'change_graphs': {},
                       'old_methods': {},
                       'new_methods': {}
                       }
            fragment_id, fragment_repo_info = next(iter(pattern['fragments_details'].items()))
            old_method, new_method = repo_loader.load_methods_mapping(fragment_repo_info)
            graphs = pattern['fragments_graphs'][fragment_id]

            print(f'Start extracting cg')
            cg = cg_loader.load_change_graph(
                repo_url=fragment_repo_info['repo']['url'],
                commit_hash=fragment_repo_info['commit']['hash'],
                old_file_path=fragment_repo_info['files']['old']['path'],
                old_method_full_name=fragment_repo_info['methods']['old']['full_name'])
            print(f'Finished extracting cg')

            # Extract node_ids corresponding to pattern
            pattern_edges = graphs[0].get_edges()
            pattern_nodes_ids = set()
            for e in pattern_edges:
                pattern_nodes_ids.add(int(e.get_source()))
                pattern_nodes_ids.add(int(e.get_destination()))
            pattern_nodes = [node for node in cg.nodes if node.id in pattern_nodes_ids]

            # Build AST of method before changes
            old_method_ast = ast.parse(old_method.get_source(), mode='exec')
            old_method_tokenized_ast = asttokens.ASTTokens(old_method.get_source(), tree=old_method_ast)

            # Extract only changed AST subtrees from pattern
            extractor = PatternSubtreesExtractor(pattern_nodes)
            subtrees = extractor.get_changed_subtrees(old_method_tokenized_ast.tree)
            result.setdefault(len(subtrees), 0)
            result[len(subtrees)] += 1

            print(f'Checked pattern {pattern_id}, found {len(subtrees)} subtrees')
            print(result)

        except Exception as e:
            print(f'Failed pattern {pattern_id}')
            print(e)
            continue
