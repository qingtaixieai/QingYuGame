import assert from 'node:assert/strict';
import {readFileSync,writeFileSync} from 'node:fs';
import {randomBytes} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {homedir} from 'node:os';
import {fileURLToPath} from 'node:url';
import {WebSocket} from 'ws';
const base='https://qingtaixieai.com',users=[],sockets=[],checks=[];
const file=new URL('../.local/production-battle-ids.json',import.meta.url);
const sshArgs=['-i',homedir()+'/.ssh/qingyu_deploy','-o','IdentitiesOnly=yes','-o','BatchMode=yes'];
const password=readFileSync(new URL('../.local/admin-access.txt',import.meta.url),'utf8').split(/\r?\n/).find(l=>l.startsWith('管理员密码：'))?.slice('管理员密码：'.length);assert(password);
async function call(path,body,cookie='',status=200){const r=await fetch(base+'/api'+path,{method:body===undefined?'GET':'POST',headers:{Origin:base,Cookie:cookie,'X-World-Request':'1','Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(15000)});const data=await r.json();assert.equal(r.status,status,path+': '+JSON.stringify(data));return {data,cookie:r.headers.get('set-cookie')?.split(';')[0]||cookie};}
async function connect(u){const ws=new WebSocket('wss://qingtaixieai.com/ws',{headers:{Origin:base,Cookie:u.cookie}});sockets.push(ws);await new Promise((r,j)=>{ws.once('open',r);ws.once('error',j)});return ws;}
const pause=ms=>new Promise(r=>setTimeout(r,ms)),passed=s=>{checks.push(s);console.log('PASS '+s);};
try{
 const admin=await call('/login',{username:'admin',password}),world=(await call('/world',undefined,admin.cookie)).data;
 const suffix=Date.now().toString(36),pass=randomBytes(18).toString('base64url');
 for(let i=0;i<3;i++){const credentials={username:`deploy_qa_${suffix}_${i}`,password:pass};await call('/register',credentials);const u=await call('/login',credentials);users.push(u);writeFileSync(file,JSON.stringify(users.map(u=>({id:u.data.id,username:u.data.username}))));await call(`/admin/accounts/${u.data.id}/approval`,{approved:true},admin.cookie);await connect(u);}
 execFileSync('scp',[...sshArgs,fileURLToPath(file),'ubuntu@49.233.163.49:/home/ubuntu/production-battle-ids.json'],{stdio:'pipe'});
 const cell=JSON.parse(execFileSync('ssh',[...sshArgs,'ubuntu@49.233.163.49','sudo -n python3 /home/ubuntu/prepare-battle-smoke.py /home/ubuntu/production-battle-ids.json'],{encoding:'utf8'}));
 const [a,b,c]=users;sockets[1].close();await pause(600);
 const players=(await call('/players',undefined,a.cookie)).data.players;assert(players.filter(p=>p.q===cell.q&&p.r===cell.r).every(p=>users.some(u=>u.data.id===p.id)));
 const battle=(await call('/battle/start',{targetId:b.data.id,version:world.version},a.cookie)).data.id;
 let state=(await call('/battle/current',undefined,a.cookie)).data;assert.equal(state.actors.length,2);assert(state.actors.every(h=>Math.max(Math.abs(h.q),Math.abs(h.r),Math.abs(h.q+h.r))<state.radius));assert.equal(state.edges.length,6);passed('Production offline target and interior random spawn');
 await call('/move',{q:cell.q,r:cell.r,version:world.version},c.cookie,400);await call('/move',{q:cell.q,r:cell.r,version:world.version},a.cookie,400);passed('Production world blockade and combat action lock');
 await call('/battle/join',{battleId:battle,version:world.version},c.cookie);state=(await call('/battle/current',undefined,c.cookie)).data;const newcomer=state.actors.find(x=>x.accountId===c.data.id);assert.equal(newcomer.q,state.radius);assert.equal(newcomer.entryRound,state.round+1);passed('Production directional reinforcement waits until next round');
 writeFileSync(new URL('../.local/production-battle-report.json',import.meta.url),JSON.stringify({date:new Date().toISOString(),checks},null,2));
}finally{for(const s of sockets)s.close();if(users.length===3){execFileSync('scp',[...sshArgs,fileURLToPath(file),'ubuntu@49.233.163.49:/home/ubuntu/production-battle-ids.json'],{stdio:'pipe'});execFileSync('ssh',[...sshArgs,'ubuntu@49.233.163.49','sudo -n python3 /home/ubuntu/cleanup-production-battle.py /home/ubuntu/production-battle-ids.json'],{stdio:'pipe'});console.log('Disposable production accounts and battle removed');}}
