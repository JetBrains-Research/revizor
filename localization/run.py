import ast
import asttokens
from _ast import AST
from typing import Optional, Dict

from localization.subtrees import SubtreeSeeker
from preprocessing.traverse import Subtree


def locate_pattern(pattern_subtree: Subtree, target_method_src: bytes) -> Optional[Dict[AST, AST]]:
    target_method_ast = ast.parse(target_method_src, mode='exec')
    target_method_tokenized_ast = asttokens.ASTTokens(target_method_src, tree=target_method_ast)
    seeker = SubtreeSeeker(pattern_subtree)
    return seeker.find_ast_subtree(target_node=target_method_tokenized_ast.tree,
                                   pattern_node=pattern_subtree.root)
