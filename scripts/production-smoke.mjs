import assert from 'node:assert/strict';
import {readFileSync,writeFileSync} from 'node:fs';
import {randomBytes} from 'node:crypto';
import {WebSocket} from 'ws';
const base='https://qingtaixieai.com';
const access=readFileSync('../.local/admin-access.txt','utf8');
const password=access.split(/\r?\n/).find(l=>l.startsWith('管理员密码：')).slice('管理员密码：'.length);
const users=[],sockets=[],checks=[];
async function api(path,body,cookie='',expected=200){
  const res=await fetch(base+'/api'+path,{method:body===undefined?'GET':'POST',headers:{Origin:base,'Content-Type':'application/json','X-World-Request':'1',Cookie:cookie},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(15000)});
  const data=await res.json().catch(()=>({}));assert.equal(res.status,expected,path+': '+JSON.stringify(data));
  const setCookie=res.headers.get('set-cookie');
  if(path==='/login'&&expected===200){assert(setCookie.includes('Secure'));assert(setCookie.includes('HttpOnly'));assert(setCookie.includes('SameSite=Strict'));}
  return {data,cookie:setCookie?.split(';')[0]||cookie};
}
function passed(s){checks.push(s);console.log('PASS '+s);}
async function waitFor(fn){let start=Date.now();while(!fn()){if(Date.now()-start>15000)throw Error('Public synchronization timeout');await new Promise(r=>setTimeout(r,100));}}
async function socket(cookie){const ws=new WebSocket(base.replace('https:','wss:')+'/ws',{headers:{Cookie:cookie,Origin:base},handshakeTimeout:15000});const state={ws,last:null};sockets.push(ws);ws.on('message',m=>state.last=JSON.parse(m));await new Promise((r,j)=>{ws.once('open',r);ws.once('error',j);});return state;}
const admin=await api('/login',{username:'admin',password});
try{
  passed('HTTPS admin login and secure session cookie');
  await api('/world',undefined,'',401);passed('anonymous world access denied');
  const world=(await api('/world',undefined,admin.cookie)).data;
  for(let i=0;i<2;i++){
    const credentials={username:`deploy_qa_${Date.now().toString(36)}_${i}`,password:randomBytes(16).toString('base64url')};
    await api('/register',credentials);const u=await api('/login',credentials);users.push(u);
    writeFileSync('../.local/production-test-ids.json',JSON.stringify(users.map(u=>({id:u.data.id,username:u.data.username}))));
    await api('/world',undefined,u.cookie,403);await api('/admin/accounts',undefined,u.cookie,403);
    await api(`/admin/accounts/${u.data.id}/approval`,{approved:true},admin.cookie);
  }
  passed('public registration, pending gate and admin approval');
  const a=await socket(users[0].cookie),b=await socket(users[1].cookie);
  await waitFor(()=>[a,b].every(s=>users.every(u=>s.last?.players.some(p=>p.id===u.data.id&&p.online))));
  passed('two public WSS connections share online presence');
  const destination=world.tiles.find(t=>t.place?.name==='白石城');
  await api('/move',{q:destination.q,r:destination.r,version:world.version},users[0].cookie);
  await waitFor(()=>[a,b].every(s=>s.last?.players.some(p=>p.id===users[0].data.id&&p.q===destination.q&&p.r===destination.r&&!p.moving)));
  passed('movement visible to both public clients');
  const reloaded=(await api('/players',undefined,users[0].cookie)).data.players.find(p=>p.id===users[0].data.id);
  assert.equal(reloaded.q,destination.q);assert.equal(reloaded.r,destination.r);passed('reloaded player state retains destination');
  await api(`/admin/accounts/${users[1].data.id}/approval`,{approved:false},admin.cookie);
  await waitFor(()=>b.ws.readyState===WebSocket.CLOSED);await api('/world',undefined,users[1].cookie,403);passed('public revocation closes connection and denies reentry');
  writeFileSync('../.local/production-report.json',JSON.stringify({date:new Date().toISOString(),url:base,checks,passed:checks.length},null,2));
}finally{
  for(const ws of sockets)ws.close();
  for(const u of users)await api(`/admin/accounts/${u.data.id}/approval`,{approved:false},admin.cookie).catch(()=>{});
}
