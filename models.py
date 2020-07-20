from collections import OrderedDict


class Subtree(OrderedDict):
    def __init__(self, *args, **kwargs):
        super(Subtree, self).__init__(*args, **kwargs)

    @property
    def root(self):
        return next(iter(self.items()))[0]
