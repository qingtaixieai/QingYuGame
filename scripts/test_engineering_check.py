import importlib.util
import tempfile
import unittest
from pathlib import Path


class EngineeringCheckTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location('engineering_check', Path(__file__).with_name('engineering_check.py'))
        cls.check = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.check)

    def test_routes_include_prefix_and_detect_duplicates(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'A.java').write_text('@RestController\n@RequestMapping("/api")\nclass A { @GetMapping("/x") public Object x(){} }')
            (root / 'B.java').write_text('@RestController\n@RequestMapping("/api")\nclass B { @GetMapping("/x") public Object x(){} @PostMapping("/y") public Object y(){} }')
            routes, errors = self.check.scan_routes(root)
            self.assertEqual(set(routes), {'GET /api/x', 'POST /api/y'})
            self.assertTrue(any('duplicate' in e for e in errors))

    def test_contract_missing_and_added_are_both_errors(self):
        errors = self.check.compare_routes({'GET /api/new'}, {'GET /api/old'})
        self.assertEqual(len(errors), 2)

    def test_owner_requires_exactly_one_match(self):
        manifest = {'world': ['src/World*'], 'combat': ['src/Battle*']}
        self.assertEqual(self.check.owners('src/WorldMap.java', manifest), ['world'])
        self.assertEqual(self.check.owners('src/New.java', manifest), [])
        manifest['integration'] = ['src/*']
        self.assertEqual(len(self.check.owners('src/WorldMap.java', manifest)), 2)

    def test_links_ignore_web_but_report_missing_local(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            page = root / 'entry.md'
            page.write_text('[ok](https://example.com) [missing](missing.md) [anchor](#part)')
            self.assertEqual(len(self.check.check_links(page)), 1)


if __name__ == '__main__':
    unittest.main()
