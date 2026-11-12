"""Dependency-free local QA runner. No deployment and no database mutations."""
import argparse
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
from urllib.parse import urljoin, urlparse
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[1]

def asset_urls(html, base):
    values = re.findall(r'(?:src|href)=["\']([^"\']+)["\']', html)
    return sorted({urljoin(base, v) for v in values if urlparse(urljoin(base,v)).netloc == urlparse(base).netloc and urlparse(v).path.endswith(('.js','.css'))})

def battle_flags(row, now):
    flags=[]
    if now-int(row['turn_deadline'])>5000: flags.append('overdue')
    if not row['actor_present']: flags.append('missing_actor')
    if row['turn_points']<0: flags.append('negative_ap')
    return flags

def run(name, cmd, cwd, out, timeout=240, env=None):
    started=time.monotonic()
    executable=shutil.which(cmd[0])
    if not executable: return {'name':name,'status':'blocked','reason':cmd[0]+' not found'}
    try:
        with (out/(name+'.log')).open('wb') as log:
            result=subprocess.run([executable,*cmd[1:]],cwd=cwd,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=timeout)
        status='pass' if result.returncode==0 else ('blocked' if name in ('browser','battle-db') and result.returncode==2 else 'fail')
        return {'name':name,'status':status,'exit':result.returncode,'seconds':round(time.monotonic()-started,2),'log':str(out/(name+'.log'))}
    except subprocess.TimeoutExpired:
        return {'name':name,'status':'fail','reason':'timeout','log':str(out/(name+'.log'))}

def fetch(url):
    with urlopen(url,timeout=15) as response:
        return response.read(),response.headers.get('Content-Type','')

def deployment(url, dist):
    checks=[]
    try:
        body,_=fetch(urljoin(url,'/api/health'))
        data=json.loads(body)
        ok=data.get('status') in ('ok','UP') or data.get('ok') is True
        checks.append({'name':'health','status':'pass' if ok else 'fail'})
        body,_=fetch(url)
        assets=asset_urls(body.decode('utf-8'),url)
        checks.append({'name':'entry-assets','status':'pass' if assets else 'fail','count':len(assets)})
        for asset in assets:
            content,mime=fetch(asset)
            valid=bool(content) and ('javascript' in mime if urlparse(asset).path.endswith('.js') else 'text/css' in mime)
            check={'name':urlparse(asset).path,'status':'pass' if valid else 'fail','sha256':hashlib.sha256(content).hexdigest()}
            if dist:
                candidate=(dist/urlparse(asset).path.lstrip('/')).resolve()
                if not candidate.is_relative_to(dist.resolve()): raise ValueError('asset outside dist')
                check['matches_local']=candidate.exists() and candidate.read_bytes()==content
                if not check['matches_local']: check['status']='fail'
            checks.append(check)
        if dist:
            checks.append({'name':'index-version','status':'pass' if (dist/'index.html').read_bytes()==body else 'fail'})
    except Exception as exc:
        checks.append({'name':'deployment','status':'fail','reason':type(exc).__name__})
    return checks

def main():
    if hasattr(sys.stdout,'reconfigure'): sys.stdout.reconfigure(encoding='utf-8')
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode',choices=['local','browser','battle','deploy'])
    parser.add_argument('--url',default='http://127.0.0.1:5173')
    parser.add_argument('--dist',type=Path,help='Optional expected frontend/dist; mismatch is failure')
    parser.add_argument('--snapshot',type=Path,help='Battle diagnostic JSON exported by battle-diagnose.mjs')
    parser.add_argument('--log',type=Path,help='Backend log: only error counts are exported')
    parser.add_argument('--java-home',type=Path,help='JDK21+ override for this run only')
    args=parser.parse_args()
    out=ROOT/'.local'/'devcheck'/datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    out.mkdir(parents=True)
    checks=[]
    env=os.environ.copy()
    if args.java_home:
        env['JAVA_HOME']=str(args.java_home.resolve())
        env['PATH']=str(args.java_home.resolve()/'bin')+os.pathsep+env.get('PATH','')
    if args.mode=='local':
        for name,cmd,cwd in [
            ('qa-tests',[sys.executable,'-m','unittest','test_devcheck','-q'],ROOT/'scripts'),
            ('frontend',['npm','run','build'],ROOT/'frontend'),
            ('pure-tests',['node','--experimental-strip-types','--test','battle-inventory.test.mjs','ferry-motion.test.mjs','map-gestures-test.mjs'],ROOT/'scripts'),
            ('backend',['mvn','-B','verify'],ROOT/'backend')]:
            checks.append(run(name,cmd,cwd,out,env=env))
    elif args.mode=='deploy': checks=deployment(args.url,args.dist)
    elif args.mode=='browser':
        checks.append(run('browser',['node','qa-browser.mjs',args.url,str(out)],ROOT/'scripts',out,120))
    elif args.mode=='battle':
        if args.snapshot:
            data=json.loads(args.snapshot.read_text(encoding='utf-8'))
            rows=data['battles']
            now=data['sampledAt']
            findings=[{'id':r['id'],'flags':battle_flags(r,now)} for r in rows]
            checks.append({'name':'battle-snapshot','status':'warn' if any(r['flags'] for r in findings) else 'pass','findings':findings,'note':'单次过期快照只是疑点，需间隔采样确认；空战场不代表战斗功能已通过。'})
        else:
            checks.append(run('battle-db',['node','battle-diagnose.mjs',str(out/'battle.json')],ROOT/'scripts',out,20))
        if args.log:
            with args.log.open('rb') as f:
                f.seek(max(0,args.log.stat().st_size-1024*1024))
                tail=f.read().decode('utf-8',errors='replace')
            checks.append({'name':'log-tail','status':'info','counts':{k:len(re.findall(k,tail,re.I)) for k in ['ERROR','Exception','deadlock','timeout']},'note':'末尾1MB计数，不输出可能包含敏感数据的原文。'})
    report={'mode':args.mode,'checks':checks,'note':'未运行写数据的联测、未部署。浏览器性能是桌面模拟采样，不代表手机真机。'}
    (out/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'report':str(out/'report.json'),'checks':checks},ensure_ascii=False,indent=2))
    return 1 if any(c['status'] in ('fail','blocked') for c in checks) else 0

if __name__=='__main__': sys.exit(main())
