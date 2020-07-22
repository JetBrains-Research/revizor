import ast

from astunparse import unparse

import pyflowgraph
from localization.subgraphs import SubgraphSeeker
from localization.utils import load_nx_graph_from_pyflowgraph, load_nx_graph_from_dot_file
from transforming import PatternBasedTransformer


def transform_method_using_pattern(target_method_path: str, fixed_method_path: str, target_pattern_graph_path: str):
    # Create and load graphs
    pfg = pyflowgraph.build_from_file(target_method_path)
    # pyflowgraph.visual.export_graph_image(pfg, 'temp_pfg.dot')
    target_method_graph = load_nx_graph_from_pyflowgraph(pfg)
    pattern_graph = load_nx_graph_from_dot_file(target_pattern_graph_path)

    # Extract isomorphic subgraph
    seeker = SubgraphSeeker(target_method_graph)
    found = seeker.find_isomorphic_subgraphs(pattern_graph)
    if found is not None:
        subgraph_mapping = next(found)
        target_label_by_node = {}
        for target_node_id, pattern_node_id in subgraph_mapping.items():
            # Check if current pattern node is mapped to another one
            mapped_node_id = None
            for succ_node_id in pattern_graph.successors(pattern_node_id):
                edge_data = pattern_graph.edges[pattern_node_id, succ_node_id, 0]
                if edge_data['xlabel'] == 'map':
                    mapped_node_id = succ_node_id
                    break
            if mapped_node_id is not None:
                # Extract target original label
                label = pattern_graph.nodes[mapped_node_id]['label']
                original_label = label[label.find("(") + 1:label.find(")")]

                # Extract AST node to change from target method
                for fg_node in pfg.nodes:
                    if fg_node.statement_num == target_node_id:
                        target_label_by_node[fg_node.ast] = original_label
                        break

        # Change mapped nodes using NodeTransformer
        changed_ast = ast.fix_missing_locations(
            PatternBasedTransformer(target_label_by_node).visit(pfg.entry_node.ast))

        # Save changed AST
        target_src = unparse(changed_ast)[2:]
        with open(fixed_method_path, 'w') as file:
            file.write(target_src)
