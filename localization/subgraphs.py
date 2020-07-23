from typing import Generator, Any, Optional

import networkx as nx
from networkx.algorithms import isomorphism


class SubgraphSeeker:
    def __init__(self, target_method_graph: nx.MultiDiGraph):
        self.target_method_graph = target_method_graph

    def find_isomorphic_subgraphs(self, pattern_graph: nx.MultiDiGraph) -> Optional[Generator[dict, Any, None]]:
        pattern_graph_before = pattern_graph.subgraph(
            [node for node, attrs in pattern_graph.nodes.items() if attrs['color'] == 'red2'])
        matcher = isomorphism.MultiDiGraphMatcher(G1=self.target_method_graph,
                                                  G2=pattern_graph_before,
                                                  node_match=self._are_nodes_equal)
        if not matcher.subgraph_is_isomorphic():
            return None
        return matcher.subgraph_isomorphisms_iter()

    @staticmethod
    def _are_nodes_equal(u, v):
        if u.get('label', None) is None or v.get('label', None) is None:
            return False
        if u['label'].startswith('var') and v['label'].startswith('var'):
            return True
        return u['label'] == v['label']
