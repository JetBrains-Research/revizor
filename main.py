import ast
import os
import pickle
import asttokens

import config
from localization.run import locate_pattern_by_subtree, locate_pattern_by_subgraph
from preprocessing.loaders import PatternLoader
from preprocessing.traverse import PatternSubtreesExtractor

if __name__ == '__main__':
    with open('../data/fragments-10-103.pickle', 'rb') as f:
        pattern = pickle.load(f)

    # Load pattern's data and about certain fragment
    fragment_id = 1280953
    graphs = pattern['fragments_graphs'][fragment_id]
    old_method, new_method = pattern['old_methods'][fragment_id], pattern['new_methods'][fragment_id]
    cg = pattern['change_graphs'][fragment_id]

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
    subtree = extractor.get_changed_subtrees(old_method_tokenized_ast.tree)[0]

    # Locate in target method
    locate_pattern_by_subtree([subtree], 'examples/103.py')

    # Locate using subgraphs
    pattern_loader = PatternLoader(config.PATTERNS_OUTPUT_ROOT)
    patterns_graphs_paths = []
    for pattern_id in range(1, 549):
        current_pattern_path = pattern_loader.patterns_path_by_id.get(pattern_id, None)
        if current_pattern_path is None:
            continue
        for filename in os.listdir(current_pattern_path):
            if filename.startswith('fragment') and filename.endswith('.dot'):
                dot_graph_path = os.path.join(current_pattern_path, filename)
                patterns_graphs_paths.append(dot_graph_path)
                break
    locate_pattern_by_subgraph(patterns_graphs_paths, 'examples/103.py')

    print('Done')
