def foobar(self, t):
    self.assertEqual(t.render(), """HTML Context""")
    print('Smth')
    self.assertEqual(t.render(), """HTML Context""")
    self.assertEqual(t.render(), """HTML Context""")
    self.assertEqual(t.render(), """HTML Context""")

