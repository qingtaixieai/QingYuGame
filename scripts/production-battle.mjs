import assert from 'node:assert/strict';
import {readFileSync,writeFileSync} from 'node:fs';
import {randomBytes} from 'node:crypto';
import {WebSocket} from 'ws';

// Run only after deploying V4. The exact account IDs are saved for server-side cleanup.
const base='https://qingtaixieai.com',origin=base;
const password=readFileSync('../.local/admin-access.txt','utf8').split(/\r?\n/).find(line=>line.startsWith('管理员密码：'))?.slice('管理员密码：'.length);
if(!password)throw Error('Production administrator credential unavailable');
const users=[],sockets=[],checks=[];
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function call(path,body,cookie='',expected=200){
  const response=await fetch(base+'/api'+path,{method:body===undefined?'GET':'POST',headers:{Origin:origin,Cookie:cookie,'X-World-Request':'1','Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(20000)});
  const data=await response.json().catch(()=>({}));
  assert.equal(response.status,expected,`${path}: ${JSON.stringify(data)}`);
  return {data,cookie:response.headers.get('set-cookie')?.split(';')[0]||cookie};
}
async function connect(user){
  const ws=new WebSocket('wss://qingtaixieai.com/ws',{headers:{Origin:origin,Cookie:user.cookie}});sockets.push(ws);
  const stream={ws,last:null};ws.on('message',raw=>{const message=JSON.parse(raw);if(message.type==='state')stream.last=message;});
  await new Promise((resolve,reject)=>{ws.once('open',resolve);ws.once('error',reject);});return stream;
}
async function until(predicate,label){for(let n=0;n<100;n++){if(predicate())return;await pause(100);}throw Error(label);}
function passed(message){checks.push(message);console.log('PASS '+message);}

try{
  const admin=await call('/login',{username:'admin',password});
  const world=(await call('/world',undefined,admin.cookie)).data;
  const suffix=Date.now().toString(36),sharedPassword=randomBytes(16).toString('base64url');
  for(let n=0;n<3;n++){
    const credentials={username:`deploy_qa_${suffix}_${n}`,password:sharedPassword};
    await call('/register',credentials);
    const user=await call('/login',credentials);users.push(user);
    writeFileSync('../.local/production-battle-ids.json',JSON.stringify(users.map(u=>({id:u.data.id,username:u.data.username}))));
    await call(`/admin/accounts/${user.data.id}/approval`,{approved:true},admin.cookie);
  }
  const [a,b,c]=users,sa=await connect(a),sb=await connect(b),sc=await connect(c);
  await until(()=>sa.last?.players.filter(p=>users.some(u=>u.data.id===p.id)).length===3,'presence');
  sb.ws.close();await until(()=>sa.last?.players.find(p=>p.id===b.data.id)?.online===false,'offline actor');
  const started=(await call('/battle/start',{targetId:b.data.id,version:world.version},a.cookie)).data;
  await until(()=>sa.last?.battles.some(x=>x.id===started.id),'battle broadcast');
  assert.equal(sa.last.players.find(p=>p.id===b.data.id).inBattle,true);
  assert.equal((await call('/battle/current',undefined,b.cookie)).data.active,true);
  passed('Offline same-cell player joins persistent battle');
  await call('/move',{q:world.spawn.q,r:world.spawn.r,version:world.version},a.cookie,400);
  await call('/collect',{q:world.spawn.q,r:world.spawn.r,version:world.version},a.cookie,400);
  await call('/emote',{code:'happy',version:world.version},a.cookie,400);
  passed('World actions rejected during combat');
  await call('/battle/join',{battleId:started.id,version:world.version},c.cookie);
  const reinforcement=(await call('/battle/current',undefined,c.cookie)).data;
  assert.equal(reinforcement.actors.find(x=>x.accountId===c.data.id).entryRound,reinforcement.round+1);
  await call('/battle/step',{battleId:started.id,q:0,r:0},a.cookie);
  await call('/battle/attack',{battleId:started.id,targetId:b.data.id},a.cookie);
  const visible=(await call('/battle/current',undefined,c.cookie)).data;
  assert(visible.intents.some(x=>x.targetId===b.data.id&&x.q===1&&x.r===0));
  passed('Reinforcement and public red attack cell synchronize through WSS');
  writeFileSync('../.local/production-battle-report.json',JSON.stringify({date:new Date().toISOString(),passed:checks.length,checks},null,2));
}finally{for(const ws of sockets)ws.close();}
