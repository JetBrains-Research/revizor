from typing import List, Optional

from common.models import AdjacencyList


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
