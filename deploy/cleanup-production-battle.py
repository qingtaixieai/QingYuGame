"""Remove only the exact disposable accounts and battle made by production-battle.mjs."""
import json
import pathlib
import re
import subprocess
import sys
import uuid

targets=json.loads(pathlib.Path(sys.argv[1]).read_text())
if len(targets)!=3:
    raise ValueError('Expected exactly three deployment test accounts')
ids=[]
for target in targets:
    identifier=str(uuid.UUID(target['id']))
    if not re.fullmatch(r'deploy_qa_[a-z0-9]+_[012]',target['username']):
        raise ValueError('Unexpected account name')
    ids.append(identifier)
if len(set(ids))!=3:
    raise ValueError('Duplicate account IDs')

pairs=','.join(f"('{target['id']}'::uuid,'{target['username']}')" for target in targets)
id_list=','.join(f"'{identifier}'::uuid" for identifier in ids)
quoted_ids=','.join(f"'{identifier}'" for identifier in ids)
sql=f"""
BEGIN;
DO $$ BEGIN
  IF (SELECT count(*) FROM accounts WHERE (id,username) IN ({pairs})) <> 3 THEN
    RAISE EXCEPTION 'Disposable account identity check failed';
  END IF;
  IF EXISTS (
    SELECT 1 FROM battle_actors
    WHERE encounter_id IN (SELECT encounter_id FROM battle_actors WHERE account_id IN ({id_list}))
      AND account_id NOT IN ({id_list})
  ) THEN RAISE EXCEPTION 'Battle includes a non-test participant'; END IF;
END $$;
DELETE FROM battle_encounters WHERE id IN (SELECT encounter_id FROM battle_actors WHERE account_id IN ({id_list}));
DELETE FROM admin_audit WHERE target IN ({quoted_ids});
DELETE FROM accounts WHERE (id,username) IN ({pairs});
COMMIT;
"""
subprocess.run(['runuser','-u','postgres','--','psql','-X','-v','ON_ERROR_STOP=1','-d','worldgame'],input=sql,text=True,check=True)
print('Disposable battle and three accounts removed.')
