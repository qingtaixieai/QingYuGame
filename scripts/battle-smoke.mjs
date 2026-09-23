import assert from 'node:assert/strict';
import {readFileSync,writeFileSync} from 'node:fs';
import {randomBytes} from 'node:crypto';
import {WebSocket} from 'ws';
import pg from 'pg';

// Local isolated database only. This creates disposable accounts without rebuilding the world.
const base='http://127.0.0.1:8080',origin='http://127.0.0.1:5173';
const db=new pg.Client({host:'127.0.0.1',port:55432,user:'worldgame',database:'worldgame'});
const users=[],sockets=[],battleIds=[],checks=[];
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function call(path,body,cookie='',expected=200){
  const response=await fetch(base+'/api'+path,{method:body===undefined?'GET':'POST',headers:{Origin:origin,Cookie:cookie,'X-World-Request':'1','Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});
  const data=await response.json().catch(()=>({}));
  assert.equal(response.status,expected,`${path}: ${JSON.stringify(data)}`);
  return {data,cookie:response.headers.get('set-cookie')?.split(';')[0]||cookie};
}
async function connect(user){
  const ws=new WebSocket('ws://127.0.0.1:8080/ws',{headers:{Origin:origin,Cookie:user.cookie}});
  const stream={ws,last:null};sockets.push(ws);
  ws.on('message',message=>{const data=JSON.parse(message);if(data.type==='state')stream.last=data;});
  await new Promise((resolve,reject)=>{ws.once('open',resolve);ws.once('error',reject);});
  return stream;
}
async function until(predicate,label,timeout=10000){const start=Date.now();while(!predicate()){if(Date.now()-start>timeout)throw Error(label);await pause(70);}}
function passed(label){checks.push(label);console.log('PASS '+label);}
const distance=(a,b)=>Math.max(Math.abs(a.q-b.q),Math.abs(a.r-b.r),Math.abs(a.q+a.r-b.q-b.r));
function path(start,exits,occupied,radius){
  const key=h=>`${h.q},${h.r}`,seen=new Set([key(start)]),queue=[[start,[]]];
  const dirs=[[1,0],[-1,0],[0,1],[0,-1],[1,-1],[-1,1]];
  while(queue.length){const [at,steps]=queue.shift();if(steps.length&&exits.some(e=>key(e)===key(at)))return steps;
    for(const [dq,dr] of dirs){const next={q:at.q+dq,r:at.r+dr};if(distance(next,{q:0,r:0})>radius||occupied.has(key(next))||seen.has(key(next)))continue;seen.add(key(next));queue.push([next,[...steps,next]]);}}
  throw Error('no exit route');
}
await db.connect();
try{
  const admin=await call('/login',JSON.parse(readFileSync('../.local/dev.json','utf8')));
  const world=(await call('/world',undefined,admin.cookie)).data;
  const suffix=Date.now().toString(36),password=randomBytes(16).toString('base64url');
  for(let n=0;n<3;n++){
    const credentials={username:`battle_${suffix}_${n}`,password};
    await call('/register',credentials);
    const user=await call('/login',credentials);users.push(user);
    await call(`/admin/accounts/${user.data.id}/approval`,{approved:true},admin.cookie);
  }
  const [a,b,c]=users,sa=await connect(a),sb=await connect(b),sc=await connect(c);
  await until(()=>sa.last?.players.filter(p=>[a,b,c].some(u=>u.data.id===p.id)).length===3,'presence');
  sb.ws.close();await until(()=>sa.last?.players.find(p=>p.id===b.data.id)?.online===false,'offline target');
  const started=(await call('/battle/start',{targetId:b.data.id,version:world.version},a.cookie)).data;
  battleIds.push(started.id);
  await until(()=>sa.last?.battles.some(x=>x.id===started.id),'battle broadcast');
  assert.equal(sa.last.players.find(p=>p.id===b.data.id).inBattle,true);
  assert.equal((await call('/battle/current',undefined,b.cookie)).data.active,true);
  passed('offline target enters shared battle and world sprites show combat');
  await call('/move',{q:world.spawn.q,r:world.spawn.r,version:world.version},a.cookie,400);
  await call('/collect',{q:world.spawn.q,r:world.spawn.r,version:world.version},a.cookie,400);
  await call('/emote',{code:'happy',version:world.version},a.cookie,400);
  passed('world movement, gathering and map emotes are blocked during combat');
  await call('/battle/start',{targetId:b.data.id,version:world.version},c.cookie,400);
  await call('/battle/join',{battleId:started.id,version:world.version},c.cookie);
  let state=(await call('/battle/current',undefined,c.cookie)).data;
  assert.equal(state.actors.find(x=>x.accountId===c.data.id).entryRound,state.round+1);
  passed('same world cell has one battle and reinforcement waits for next round');
  await call('/battle/step',{battleId:started.id,q:0,r:0},a.cookie);
  await call('/battle/attack',{battleId:started.id,targetId:b.data.id},a.cookie);
  state=(await call('/battle/current',undefined,c.cookie)).data;
  assert(state.intents.some(i=>i.targetId===b.data.id&&i.q===1&&i.r===0));
  passed('adjacent attack draws a public pending red cell');
  const sbAgain=await connect(b);
  await call('/battle/end-turn',{battleId:started.id},a.cookie);
  await until(()=>sbAgain.last?.battleRevision>0,'target turn');
  assert.equal((await call('/battle/current',undefined,b.cookie)).data.turnAccountId,b.data.id);
  await call('/battle/step',{battleId:started.id,q:2,r:0},b.cookie);
  state=(await call('/battle/current',undefined,a.cookie)).data;
  assert.equal(state.intents.length,0);
  assert.equal(state.events.filter(e=>e.kind==='attack'&&e.actorId===a.data.id).length,1);
  await call('/battle/end-turn',{battleId:started.id},b.cookie);
  state=(await call('/battle/current',undefined,a.cookie)).data;
  assert.equal(state.events.filter(e=>e.kind==='attack'&&e.actorId===a.data.id).length,1);
  passed('leaving red cell fires the same attack immediately and never repeats');
  await call('/battle/end-turn',{battleId:started.id},a.cookie);
  await call('/battle/end-turn',{battleId:started.id},b.cookie);
  state=(await call('/battle/current',undefined,c.cookie)).data;
  assert.equal(state.turnAccountId,c.data.id);
  const cActor=state.actors.find(x=>x.accountId===c.data.id);
  for(const step of path(cActor,state.exits,new Set(state.actors.filter(x=>x.accountId!==c.data.id).map(x=>`${x.q},${x.r}`)),state.radius))
    await call('/battle/step',{battleId:started.id,...step},c.cookie);
  state=(await call('/battle/current',undefined,a.cookie)).data;
  assert.equal(state.actors.length,2);
  assert.equal(state.turnAccountId,a.data.id);
  const aActor=state.actors.find(x=>x.accountId===a.data.id);
  for(const step of path(aActor,state.exits,new Set(state.actors.filter(x=>x.accountId!==a.data.id).map(x=>`${x.q},${x.r}`)),state.radius))
    await call('/battle/step',{battleId:started.id,...step},a.cookie);
  assert.equal((await call('/battle/current',undefined,b.cookie)).data.active,false);
  await until(()=>sa.last?.players.find(p=>p.id===a.data.id)?.inBattle===false,'battle close broadcast');
  passed('edge exits remove participants; one remaining closes the battle');
  await call('/move',{q:world.spawn.q,r:world.spawn.r,version:world.version},a.cookie);
  passed('world action resumes when combat ends');
  writeFileSync('../.local/battle-report.json',JSON.stringify({date:new Date().toISOString(),passed:checks.length,checks},null,2));
}finally{
  for(const stream of sockets)stream.close();
  await pause(350);
  for(const id of battleIds)await db.query('delete from battle_encounters where id=$1',[id]);
  for(const user of users){await db.query('delete from admin_audit where target=$1',[user.data.id]);await db.query('delete from accounts where id=$1',[user.data.id]);}
  await db.end();
}
