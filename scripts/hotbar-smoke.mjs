import assert from 'node:assert/strict';
import {readFileSync,writeFileSync} from 'node:fs';
import {randomBytes} from 'node:crypto';
import {WebSocket} from 'ws';
import pg from 'pg';

const db=new pg.Client({host:'127.0.0.1',port:55432,user:'worldgame',database:'worldgame'}),users=[],sockets=[],checks=[];
let battle;
const pass=name=>{checks.push(name);console.log('PASS '+name);};
async function call(path,body,u,status=200){
 const r=await fetch('http://127.0.0.1:8080/api'+path,{method:body===undefined?'GET':'POST',headers:{Origin:'http://127.0.0.1:5173',Cookie:u?.cookie||'','X-World-Request':'1','Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});
 const data=await r.json();assert.equal(r.status,status,path+': '+JSON.stringify(data));return {data,cookie:r.headers.get('set-cookie')?.split(';')[0]||u?.cookie};
}
const state=async u=>(await call('/hotbar',undefined,u)).data;
const slot=(s,ref)=>s.slots.findIndex(x=>x.actionRef===ref);
await db.connect();
const before=(await db.query('select document from worlds where id=1')).rows[0].document;
try{
 const admin=await call('/login',JSON.parse(readFileSync(new URL('../.local/dev.json',import.meta.url),'utf8')));
 for(let i=0;i<2;i++){
  const credentials={username:'slots_'+Date.now().toString(36)+'_'+i,password:randomBytes(16).toString('base64url')};
  await call('/register',credentials);const u=await call('/login',credentials);users.push(u);
  await call(`/admin/accounts/${u.data.id}/approval`,{approved:true},admin);
  const ws=new WebSocket('ws://127.0.0.1:8080/ws',{headers:{Origin:'http://127.0.0.1:5173',Cookie:u.cookie}});sockets.push(ws);
  await new Promise((resolve,reject)=>{ws.once('message',resolve);ws.once('error',reject);});
 }
 const [a,b]=users;
 let s=await state(a);
 assert.equal(s.slots.length,30);assert.equal(s.actions.filter(x=>x.source==='weapon').length,1);assert(!s.actions.some(x=>x.id==='bandage'));
 assert(s.actions.every(x=>x.disabledReason));pass('Server supplies only owned actions, grouped in five source pages');
 const edit=async(operation,from,to,actionRef,status=200)=>{
  const r=await call('/hotbar/layout',{revision:s.revision,operation,from,to,actionRef},a,status);if(status===200)s=r.data;return r;
 };
 const first=slot(s,'move'),second=slot(s,'equipment'),oldRevision=s.revision;
 await edit('swap',first,second);assert.equal(s.slots[second].actionRef,'move');
 await call('/hotbar/layout',{revision:oldRevision,operation:'clear',from:first},a,409);
 await edit('clear',second);assert.equal(slot(await state(a),'move'),-1);
 assert.equal(slot(await state(b),'move'),first);pass('Reordering and manual clearing persist and remain account-isolated; stale writes conflict');
 await edit('assign',second,undefined,'move');await edit('lock',second);
 await edit('clear',second,undefined,undefined,400);await edit('swap',second,first,undefined,400);
 await edit('assign',first,undefined,'invented-spell',400);await edit('swap',first,6,undefined,400);
 await edit('lock',second);pass('Locked slots, unknown actions and cross-source moves are rejected');
 for(const code of ['buckler','tower_shield','spear','bandage'])await call(`/admin/accounts/${a.data.id}/items`,{code,quantity:1},admin);
 await call('/equipment',{mainHand:null,offHand:'buckler'},a);s=await state(a);
 const guard=slot(s,'guard:buckler');assert(guard>=0);assert.equal(s.actions.find(x=>x.id==='bandage').quantity,1);
 await edit('lock',guard);await call('/equipment',{mainHand:null,offHand:null},a);s=await state(a);
 assert.equal(s.slots[guard].actionRef,null);assert(s.slots[guard].locked);
 await call('/equipment',{mainHand:null,offHand:'buckler'},a);s=await state(a);assert.notEqual(slot(s,'guard:buckler'),guard);
 pass('Equipment loss removes actions but preserves slot locks; reacquisition uses an unlocked slot');
 const world=(await call('/world',undefined,admin)).data,occupied=(await db.query('select q,r from accounts where entered=true')).rows;
 const cell=world.tiles.find(t=>t.terrain==='plain'&&!occupied.some(p=>p.q===t.q&&p.r===t.r));
 for(const u of users)await db.query('update accounts set q=$1,r=$2 where id=$3',[cell.q,cell.r,u.data.id]);
 battle=(await call('/battle/start',{targetId:b.data.id,version:world.version},a)).data.id;
 await db.query('update battle_actors set q=0,r=0 where encounter_id=$1 and account_id=$2',[battle,a.data.id]);
 await db.query('update battle_actors set q=1,r=0 where encounter_id=$1 and account_id=$2',[battle,b.data.id]);
 await db.query('update battle_encounters set turn_account_id=$1,turn_points=6,turn_deadline=$2 where id=$3',[a.data.id,Date.now()+3600000,battle]);
 s=await state(a);assert(s.actions.find(x=>x.id==='bandage').disabledReason.includes('治疗'));
 assert(s.actions.find(x=>x.id==='rescue').disabledReason);assert.equal(s.actions.find(x=>x.command==='guard').cost,2);
 await db.query('update characters set hp=10 where id=$1',[a.data.id]);s=await state(a);assert.equal(s.actions.find(x=>x.id==='bandage').disabledReason,'');
 await call('/battle/bandage',{targetId:a.data.id},a);s=await state(a);assert(!s.actions.some(x=>x.id==='bandage'));assert.equal(slot(s,'bandage'),-1);
 assert.equal((await call('/battle/current',undefined,a)).data.turnPoints,4);
 pass('Bandage availability follows target health; consuming the last item removes its action and costs 2 AP');
 await call('/battle/equipment',{mainHand:null,offHand:'tower_shield'},a);s=await state(a);
 assert(!s.actions.some(x=>x.command==='attack'));assert(s.actions.some(x=>x.id==='guard:tower_shield'));
 await call('/battle/attack',{battleId:battle,q:1,r:0},a,400);
 assert.equal((await call('/battle/current',undefined,a)).data.turnPoints,2);
 pass('Battle equipment change costs 2 AP and tower shield removes attack without a bypass');
 await db.query('update battle_encounters set turn_points=1 where id=$1',[battle]);s=await state(a);
 assert(s.actions.find(x=>x.id==='equipment').disabledReason.includes('不足'));assert.equal(s.actions.find(x=>x.command==='guard').cost,1);
 await call('/battle/guard',{battleId:battle},a);s=await state(a);assert(s.actions.find(x=>x.id==='move').disabledReason);
 pass('Costs and disabled reasons track remaining AP and guard conversion');
 await call(`/admin/accounts/${a.data.id}/items`,{code:'bandage',quantity:2},admin);s=await state(a);assert.equal(s.actions.find(x=>x.id==='bandage').quantity,2);assert(slot(s,'bandage')>=0);
 const persisted=JSON.parse((await db.query('select document from action_layouts where account_id=$1',[a.data.id])).rows[0].document);
 assert.deepEqual(persisted.slots,s.slots);assert.equal((await db.query('select document from worlds where id=1')).rows[0].document,before);
 pass('Reacquired consumables return automatically; layout is stored in PostgreSQL and world map is unchanged');
 writeFileSync(new URL('../.local/hotbar-report.json',import.meta.url),JSON.stringify({date:new Date().toISOString(),checks},null,2));
}finally{
 for(const ws of sockets)ws.close();
 if(battle)await db.query('delete from battle_encounters where id=$1',[battle]);
 for(const u of users){await db.query('delete from admin_audit where target like $1',[u.data.id+'%']);await db.query('delete from accounts where id=$1',[u.data.id]);}
 await db.end();
}
