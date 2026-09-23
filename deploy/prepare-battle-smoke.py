"""Place only exact deployment-test accounts in an empty isolated cell; never rebuild world."""
import json,pathlib,re,subprocess,sys,uuid
users=json.loads(pathlib.Path(sys.argv[1]).read_text())
assert len(users)==3
for u in users:
    uuid.UUID(u['id'])
    assert re.fullmatch(r'deploy_qa_[a-z0-9]+_[012]',u['username'])
def query(sql):
    return subprocess.check_output(['runuser','-u','postgres','--','psql','-X','-At','-v','ON_ERROR_STOP=1','-d','worldgame','-c',sql],text=True).strip()
world=json.loads(query('select document from worlds where id=1'))
pairs=','.join("('%s'::uuid,'%s')"%(u['id'],u['username']) for u in users)
assert query('select count(*) from accounts where (id,username) in ('+pairs+')')=='3'
ids=','.join("'%s'::uuid"%u['id'] for u in users)
occupied=json.loads(query("select coalesce(json_agg(json_build_object('q',q,'r',r)),'[]') from accounts where entered=true and id not in ("+ids+")"))
index={(t['q'],t['r']):t for t in world['tiles']}
def walk(q,r):return (q,r) in index and index[q,r]['terrain'] not in ('mountain','ocean','river')
def distance(a,b):return max(abs(a['q']-b['q']),abs(a['r']-b['r']),abs(a['q']+a['r']-b['q']-b['r']))
cell=next(t for t in world['tiles'] if walk(t['q'],t['r']) and walk(t['q']+1,t['r']) and all(distance(t,p)>6 for p in occupied))
q,r=cell['q'],cell['r']
query('BEGIN;'+''.join("update accounts set q=%d,r=%d where id='%s'::uuid;"%(q+(i==2),r,u['id']) for i,u in enumerate(users))+'COMMIT;')
print(json.dumps({'q':q,'r':r}))
