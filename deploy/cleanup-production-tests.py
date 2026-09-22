"""Remove only exact disposable accounts created by scripts/production-smoke.mjs."""
import json
import pathlib
import re
import subprocess
import sys
import uuid

targets=json.loads(pathlib.Path(sys.argv[1]).read_text())
for target in targets:
    identifier=str(uuid.UUID(target['id']))
    name=target['username']
    if not re.fullmatch(r'deploy_qa_[a-z0-9]+_[01]',name):
        raise ValueError('Not an explicitly named deployment test account')
    subprocess.run(['runuser','-u','postgres','--','psql','-X','-v','ON_ERROR_STOP=1','-d','worldgame'],
                   input=f"BEGIN; delete from moderation_bans where target_type='account' and target='{identifier}' and exists(select 1 from accounts where id='{identifier}' and username='{name}'); delete from accounts where id='{identifier}' and username='{name}'; COMMIT;\n",text=True,check=True)
print('Disposable deployment accounts removed; other accounts unchanged.')
