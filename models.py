from _ast import AST
from collections import OrderedDict
from dataclasses import dataclass
from itertools import chain
from typing import Dict, List, Union, Mapping, Generator, Tuple

import pydot

from changegraph.models import ChangeGraph
from vcs.traverse import Method

PatternId = int
FragmentId = int
JSON = Union[str, int, float, bool, None, Mapping[str, 'JSON'], List['JSON']]


@dataclass
class FragmentRepoDetails:
    def __init__(self, details: JSON):
        self.repo_url = details['repo']['url']
        self.repo_name = details['repo']['name']
        self.commit_hash = details['commit']['hash']
        self.old_path = details['files']['old']['path']
        self.new_path = details['files']['new']['path']
        self.old_method_full_name = details['methods']['old']['full_name']
        self.new_method_full_name = details['methods']['new']['full_name']


@dataclass
class Pattern:
    fragments_details: Dict[FragmentId, FragmentRepoDetails]
    fragments_graphs: Dict[FragmentId, List[pydot.Graph]]
    change_graphs: Dict[FragmentId, ChangeGraph]
    old_methods: Dict[FragmentId, Method]
    new_methods: Dict[FragmentId, Method]

    @property
    def fragment_ids(self):
        return list(self.fragments_details.keys())


class AdjacencyList(OrderedDict):
    def __init__(self, *args, **kwargs):
        super(AdjacencyList, self).__init__(*args, **kwargs)

    def __setitem__(self, node_from: AST, nodes_to: List[AST]):
        super(AdjacencyList, self).__setitem__(node_from, nodes_to)

    @property
    def root(self) -> AST:
        return next(iter(self.items()))[0]

    @property
    def nodes(self) -> List[AST]:
        return list(set(self.keys()).union(set(chain(*self.values()))))

    @property
    def edges(self) -> Generator[Tuple[AST, AST], None, None]:
        for node_from, nodes_to in self.items():
            for node_to in nodes_to:
                yield node_from, node_to
