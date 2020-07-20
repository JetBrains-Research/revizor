import networkx as nx
import pydot

import changegraph
import pyflowgraph
from changegraph.models import ChangeNode
from networkx.algorithms import isomorphism


class SubgraphSeeker:
    def __init__(self, target_method_path):
        self.target_graph = self._load_target_graph(target_method_path)

    def find_isomorphic_subgraphs(self, pattern_graph_path):
        pattern_graph = self._read_dot(pattern_graph_path)
        pattern_graph_before = pattern_graph.subgraph(
            [node for node, attrs in pattern_graph.nodes.items() if attrs['color'] == 'red2'])
        matcher = isomorphism.MultiDiGraphMatcher(G1=self.target_graph,
                                                  G2=pattern_graph_before,
                                                  node_match=self._are_nodes_equal)
        if not matcher.subgraph_is_isomorphic():
            return None
        return matcher.subgraph_isomorphisms_iter()

    @staticmethod
    def _are_nodes_equal(u, v):
        return u['label'] == v['label']

    @staticmethod
    def _load_target_graph(target_method_path):
        target_flow_graph = pyflowgraph.build_from_file(target_method_path)
        target_graph = nx.MultiDiGraph()
        for node_from in target_flow_graph.nodes:
            cg_node_from = ChangeNode.create_from_fg_node(node_from)
            cg_label, _ = changegraph.visual._get_label_and_attrs(cg_node_from)
            cg_label_without_id = cg_label.split('[')[0]
            target_graph.add_node(node_from.statement_num, label=cg_label_without_id)
            for out_edge in node_from.out_edges:
                target_graph.add_edge(node_from.statement_num, out_edge.node_to.statement_num)
        return target_graph

    @staticmethod
    def _read_dot(dot_graph_path):
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
