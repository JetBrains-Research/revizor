import networkx as nx
import pydot

import changegraph
from changegraph.models import ChangeNode
from pyflowgraph.models import ExtControlFlowGraph


def load_nx_graph_from_dot_file(dot_graph_path: str) -> nx.MultiDiGraph:
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


def load_nx_graph_from_pyflowgraph(target_flow_graph: ExtControlFlowGraph) -> nx.MultiDiGraph:
    target_graph = nx.MultiDiGraph()
    for node_from in target_flow_graph.nodes:
        cg_node_from = ChangeNode.create_from_fg_node(node_from)
        cg_label, _ = changegraph.visual._get_label_and_attrs(cg_node_from)
        cg_label_without_id = cg_label.split('[')[0]
        target_graph.add_node(node_from.statement_num, label=cg_label_without_id)
        for out_edge in node_from.out_edges:
            target_graph.add_edge(node_from.statement_num, out_edge.node_to.statement_num)
    return target_graph
