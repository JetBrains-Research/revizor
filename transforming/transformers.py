import ast
from typing import Dict


class PatternBasedTransformer(ast.NodeTransformer):
    def __init__(self, target_label_by_node: Dict[ast.AST, str]):
        self.target_label_by_node = target_label_by_node

    def visit(self, node):
        if node not in self.target_label_by_node.keys():
            visitor = self.generic_visit
        else:
            method = 'visit_' + node.__class__.__name__
            visitor = getattr(self, method, self.generic_visit)
        return visitor(node)

    def visit_Attribute(self, node):
        original_label = self.target_label_by_node[node]
        id, attr = original_label.split('.')
        return ast.Attribute(
            value=ast.Name(id=id, ctx=node.value.ctx),
            attr=attr,
            ctx=node.ctx
        )
