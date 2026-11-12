import tempfile
import unittest
from pathlib import Path
from scripts.backend_boundary_check import PURE_MODULES, check_boundaries


class BackendBoundaryTest(unittest.TestCase):
    def test_pure_rules_reject_database_or_orchestrator_dependencies(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in PURE_MODULES:
                (root / f'{name}.java').write_text(f'class {name} {{}}')
            self.assertEqual(check_boundaries(root), [])
            (root / 'NpcCombatNavigation.java').write_text('class NpcCombatNavigation { WorldService world; JdbcTemplate db; }')
            self.assertEqual(len(check_boundaries(root)), 2)

    def test_comments_and_strings_do_not_create_false_dependencies(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in PURE_MODULES:
                (root / f'{name}.java').write_text('/* WorldService */ class X { String s="JdbcTemplate"; } // SocketConfig')
            self.assertEqual(check_boundaries(root), [])
            (root / 'WorldMap.java').unlink()
            self.assertTrue(any('missing' in error for error in check_boundaries(root)))
