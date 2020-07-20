import os

import networkx as nx
import pydot

import changegraph
import config
import pyflowgraph
from changegraph.models import ChangeNode
from preprocessing.loaders import PatternLoader
from networkx.algorithms import isomorphism


def read_dot(dot_graph_path):
    dot_pattern_graph = pydot.graph_from_dot_file(dot_graph_path)[0]
    for subgraph in dot_pattern_graph.get_subgraphs():
        for subgraph_node in subgraph.get_nodes():
            if subgraph_node.get_name() == 'graph':
                continue
            dot_pattern_graph.add_node(subgraph_node)
    for node in dot_pattern_graph.get_nodes():
        label = node.get_attributes()['label'].strip('\"')
        label_without_id = label.split('[')[0]
        node.get_attributes()['label'] = label_without_id
    return nx.drawing.nx_pydot.from_pydot(dot_pattern_graph)


if __name__ == '__main__':
    pattern_id = 103
    pattern_loader = PatternLoader(config.PATTERNS_OUTPUT_ROOT)
    current_pattern_path = pattern_loader.patterns_path_by_id[pattern_id]
    dot_graph_path = None
    for filename in os.listdir(current_pattern_path):
        if filename.startswith('fragment') and filename.endswith('.dot'):
            dot_graph_path = os.path.join(current_pattern_path, filename)
            break

    pattern_graph = read_dot(dot_graph_path)
    pattern_graph_before = pattern_graph.subgraph([node for node, attrs in pattern_graph.nodes.items()
                                                   if attrs['color'] == 'red2'])

    target_flow_graph = pyflowgraph.build_from_file('../examples/103.py')
    target_graph = nx.MultiDiGraph()
    for node_from in target_flow_graph.nodes:
        cg_node_from = ChangeNode.create_from_fg_node(node_from)
        cg_label, _ = changegraph.visual._get_label_and_attrs(cg_node_from)
        cg_label_without_id = cg_label.split('[')[0]
        target_graph.add_node(node_from.statement_num, label=cg_label_without_id)
        for out_edge in node_from.out_edges:
            target_graph.add_edge(node_from.statement_num, out_edge.node_to.statement_num)


    def node_match(u, v):
        return u['label'] == v['label']


    matcher = isomorphism.MultiDiGraphMatcher(G1=target_graph,
                                              G2=pattern_graph_before,
                                              node_match=node_match)
    print(next(matcher.subgraph_isomorphisms_iter()))
    print('Done.')
