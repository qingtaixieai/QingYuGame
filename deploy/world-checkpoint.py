"""Read-only release checkpoint. Run on the game host; never exports credentials."""
import json
import subprocess

def query(sql):
    return subprocess.check_output(['sudo','-u','postgres','psql','worldgame','-At','-c',sql],text=True).strip()

print(json.dumps({
    'world': json.loads(query('select document from worlds where id=1')),
    'players': json.loads(query("select coalesce(json_agg(row_to_json(a)), '[]') from (select id,q,r from accounts order by id) a")),
},ensure_ascii=False))
