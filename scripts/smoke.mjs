import assert from 'node:assert/strict';
import {readFileSync,writeFileSync} from 'node:fs';
import {randomBytes} from 'node:crypto';
import {WebSocket} from 'ws';
import pg from 'pg';

// Destructive integration suite: intentionally bound to the disposable LOCAL database.
const base='http://127.0.0.1:8080',origin='http://127.0.0.1:5173';
const adminCredentials=JSON.parse(readFileSync('../.local/dev.json','utf8'));
const checks=[],sockets=[],created=[];
const pause=ms=>new Promise(r=>setTimeout(r,ms));
async function request(path,body,cookie='',expected=200,extra={}){
  const res=await fetch(base+'/api'+path,{method:body===undefined?'GET':'POST',headers:{Origin:origin,'Content-Type':'application/json','X-World-Request':'1',Cookie:cookie,...extra},body:body===undefined?undefined:JSON.stringify(body)});
  if(res.status===429){await pause(61000);return request(path,body,cookie,expected,extra);}
  const data=await res.json().catch(()=>({}));assert.equal(res.status,expected,`${path}: ${JSON.stringify(data)}`);
  return {data,cookie:res.headers.get('set-cookie')?.split(';')[0]||cookie};
}
function check(name){checks.push(name);console.log('PASS '+name);}
async function connect(cookie){
  const ws=new WebSocket(base.replace('http:','ws:')+'/ws',{headers:{Cookie:cookie,Origin:origin}});sockets.push(ws);
  const item={ws,last:null,messages:0};ws.on('message',d=>{item.last=JSON.parse(d);item.messages++;});
  await new Promise((resolve,reject)=>{ws.once('open',resolve);ws.once('error',reject);});
  return item;
}
async function until(fn,message,timeout=15000){const start=Date.now();while(!fn()){if(Date.now()-start>timeout)throw Error(message);await pause(80);}}
const db=new pg.Client({host:'127.0.0.1',port:55432,user:'worldgame',database:'worldgame'});
await db.connect();
try{
  const admin=await request('/login',adminCredentials),suffix=Date.now().toString(36),password=randomBytes(15).toString('base64url');
  await request('/world',undefined,'',401);check('anonymous map access denied');
  await request('/stop',{},admin.cookie,403,{'X-World-Request':'0'});check('missing CSRF command header denied');
  await request('/stop',{},admin.cookie,403,{Origin:'https://evil.example'});check('foreign Origin denied');
  const world=(await request('/world',undefined,admin.cookie)).data;
  const people=[];
  for(let i=0;i<9;i++){
    const credentials={username:`qa_${suffix}_${i}`,password};created.push(credentials.username);
    await request('/register',credentials);
    const u=await request('/login',credentials);people.push({...u,credentials});
    await request('/world',undefined,u.cookie,403);
    await request('/admin/accounts',undefined,u.cookie,403);
    if(i===0){const state=(await request('/players',undefined,admin.cookie)).data;assert(!state.players.some(p=>p.id===u.data.id));}
    await request(`/admin/accounts/${u.data.id}/approval`,{approved:true},admin.cookie);
  }
  check('registration pending gate and admin authorization');
  const streams=await Promise.all([admin,...people].map(a=>connect(a.cookie)));
  await until(()=>streams.every(s=>s.last?.players.filter(p=>p.online).length>=10),'10-player presence missing');
  check('10 simultaneous distinct accounts receive shared presence');
  const destinations=world.tiles.filter(t=>t.place && (t.q!==world.spawn.q||t.r!==world.spawn.r));
  for(let i=0;i<people.length;i++){
    const target=destinations[i%destinations.length],u=people[i];
    const route=(await request('/move',{q:target.q,r:target.r,version:world.version},u.cookie)).data.path;
    assert(route.length>0);u.target=target;
  }
  await until(()=>people.every(u=>streams[0].last?.players.some(p=>p.id===u.data.id&&p.q===u.target.q&&p.r===u.target.r&&!p.moving)),'movement failed',20000);
  assert(streams.every(s=>s.messages>3));check('concurrent authoritative movement reaches 9 destinations');
  for(const u of people){const row=(await db.query('select q,r from accounts where id=$1',[u.data.id])).rows[0];assert.equal(row.q,u.target.q);assert.equal(row.r,u.target.r);}
  check('acknowledged movement persisted in PostgreSQL');
  const blocked=world.tiles.find(t=>t.terrain==='ocean');await request('/move',{q:blocked.q,r:blocked.r,version:world.version},people[0].cookie,400);check('impassable terrain rejected by server');
  streams[1].ws.close();await until(()=>streams[0].last.players.find(p=>p.id===people[0].data.id)?.online===false,'offline not visible');check('offline character retained at its position');
  await request(`/admin/accounts/${people[1].data.id}/approval`,{approved:false},admin.cookie);
  await until(()=>streams[2].ws.readyState===WebSocket.CLOSED,'revocation did not close socket');
  await request('/world',undefined,people[1].cookie,403);check('revocation disconnects immediately and blocks API');
  await request(`/admin/accounts/${people[1].data.id}/approval`,{approved:true},admin.cookie);await request('/world',undefined,people[1].cookie);check('approval restoration retains account');
  const resetPassword=randomBytes(15).toString('base64url');
  await request(`/admin/accounts/${people[2].data.id}/password`,{password:resetPassword},admin.cookie);
  await request('/me',undefined,people[2].cookie,401);
  await request('/login',people[2].credentials,'',401);
  await request('/login',{username:people[2].credentials.username,password:resetPassword});check('password reset invalidates sessions and old password');
  await request('/admin/regenerate',{confirmation:'wrong'},admin.cookie,400);
  const next=(await request('/admin/regenerate',{confirmation:'重新生成世界'},admin.cookie)).data;
  assert.notEqual(next.version,world.version);assert.notEqual(next.seed,world.seed);
  await until(()=>streams[0].last.version===next.version,'world reset not broadcast');
  assert(streams[0].last.players.every(p=>p.q===next.spawn.q&&p.r===next.spawn.r));
  await request('/move',{q:next.spawn.q,r:next.spawn.r,version:world.version},people[0].cookie,400);
  check('world reset atomic, broadcast, stale commands rejected');
  const list=(await request('/admin/accounts',undefined,admin.cookie)).data;assert(people.every(u=>list.some(a=>a.id===u.data.id&&a.approved)));
  check('world reset preserves accounts and approvals');
  writeFileSync('../.local/integration-report.json',JSON.stringify({date:new Date().toISOString(),checks,passed:checks.length,concurrentAccounts:10},null,2));
}finally{
  sockets.forEach(s=>s.close());await pause(400);
  for(const name of created)await db.query('delete from accounts where username=$1',[name]);
  await db.end();
}
