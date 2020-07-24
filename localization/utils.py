from typing import List, Optional

import networkx as nx
import pydot

import changegraph
from changegraph.models import ChangeNode
from models import AdjacencyList
from pyflowgraph.models import ExtControlFlowGraph


def get_maximal_subtree(subtrees: List[AdjacencyList]) -> AdjacencyList:
    max_subtree_size = 0
    max_subtree = None
    for subtree in subtrees:
        current_subtree_size = len(subtree.nodes)
        if current_subtree_size > max_subtree_size:
            max_subtree_size = current_subtree_size
            max_subtree = subtree
    return max_subtree


def get_longest_common_suffix(strings: List[str]) -> Optional[str]:
    if strings is None or len(strings) == 0:
        return None
    minimal_len = min([len(string) for string in strings])
    result_suffix = ''
    for pos in range(1, minimal_len + 1):
        current_elem = strings[0][-pos]
        if all([string[-pos] == current_elem for string in strings]):
            result_suffix += current_elem
        else:
            break
    return None if result_suffix == '' else result_suffix[::-1]


def create_nx_graph_from_pattern(path_to_pattern_fragments_graphs: List[str]) -> nx.MultiDiGraph:
    var_names = {}
    for path_to_dot_graph in path_to_pattern_fragments_graphs:
        graph = load_nx_graph_from_dot_file(path_to_dot_graph)
        var_node_num = 0
        for node_id in graph.nodes:
            label = graph.nodes[node_id]['label']
            if label.startswith('var'):
                var_names.setdefault(var_node_num, []).append(label)
                var_node_num += 1
    final_pattern_graph = load_nx_graph_from_dot_file(path_to_pattern_fragments_graphs[0])
    var_node_num = 0
    for node_id in final_pattern_graph.nodes:
        label = final_pattern_graph.nodes[node_id]['label']
        if label.startswith('var'):
            lcs = get_longest_common_suffix(var_names[var_node_num])
            final_pattern_graph.nodes[node_id]['longest_common_var_name_suffix'] = lcs
            var_node_num += 1
    return final_pattern_graph


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
