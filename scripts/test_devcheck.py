import unittest
from devcheck import asset_urls, battle_flags, deployment, run
from unittest.mock import patch
from pathlib import Path
import tempfile
import sys

class Checks(unittest.TestCase):
    def test_assets_are_same_origin_and_deduplicated(self):
        html='<script src="/assets/a.js"></script><link href="/assets/b.css"><script src="https://other.test/a.js"></script>'
        self.assertEqual(asset_urls(html,'https://game.test'), ['https://game.test/assets/a.js','https://game.test/assets/b.css'])

    def test_overdue_is_suspicion_not_proof(self):
        self.assertEqual(battle_flags({'turn_deadline':9000,'turn_points':2,'actor_present':True},10000), [])
        self.assertIn('overdue',battle_flags({'turn_deadline':1,'turn_points':2,'actor_present':True},10000))

    def test_missing_actor_and_invalid_points(self):
        self.assertEqual(battle_flags({'turn_deadline':99999,'turn_points':-1,'actor_present':False},0),['missing_actor','negative_ap'])

    def test_deploy_does_not_accept_html_as_javascript(self):
        with patch('devcheck.fetch',side_effect=[(b'{"status":"ok"}','application/json'),(b'<script src="/a.js"></script>','text/html'),(b'<html>fallback</html>','text/html')]):
            result=deployment('https://game.test',None)
        self.assertEqual(result[-1]['status'],'fail')

    def test_deploy_detects_local_version_mismatch(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            (root/'index.html').write_text('<script src="/a.js"></script>')
            (root/'a.js').write_text('old')
            with patch('devcheck.fetch',side_effect=[(b'{"status":"ok"}','application/json'),((root/'index.html').read_bytes(),'text/html'),(b'new','application/javascript')]):
                result=deployment('https://game.test',root)
            self.assertEqual(next(x for x in result if x['name']=='/a.js')['status'],'fail')

    def test_runner_preserves_failure_and_log(self):
        with tempfile.TemporaryDirectory() as d:
            result=run('fixture',[sys.executable,'-c','print("fixture failure");raise SystemExit(1)'],d,Path(d))
            self.assertEqual(result['status'],'fail')
            self.assertIn('fixture failure',Path(result['log']).read_text())

if __name__=='__main__': unittest.main()
