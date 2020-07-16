import ast
import pickle
import asttokens

from preprocessing.traverse import PatternSubtreesExtractor

if __name__ == '__main__':
    with open('../data/fragments-10-103.pickle', 'rb') as f:
        pattern = pickle.load(f)

    # Load pattern's data and about certain fragment
    fragment_id = 1280954
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
    source_code_ast = ast.parse(old_method.get_source(), mode='exec')
    tokenized_ast = asttokens.ASTTokens(old_method.get_source(), tree=source_code_ast)

    # Extract only changed AST subtrees from pattern
    extractor = PatternSubtreesExtractor(pattern_nodes)
    subtrees = extractor.get_changed_subtrees(tokenized_ast.tree)

    print('Done')
