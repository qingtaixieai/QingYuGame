import assert from 'node:assert/strict';
import {readFileSync,writeFileSync} from 'node:fs';
import {randomBytes} from 'node:crypto';
import {WebSocket} from 'ws';
import pg from 'pg';
const db=new pg.Client({host:'127.0.0.1',port:55432,user:'worldgame',database:'worldgame'}),users=[],sockets=[],checks=[];
const pass=s=>{checks.push(s);console.log('PASS '+s);};
async function call(path,body,u,status=200){const r=await fetch('http://127.0.0.1:8080/api'+path,{method:body===undefined?'GET':'POST',headers:{Origin:'http://127.0.0.1:5173',Cookie:u?.cookie||'','X-World-Request':'1','Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});const data=await r.json();assert.equal(r.status,status,path+': '+JSON.stringify(data));return {data,cookie:r.headers.get('set-cookie')?.split(';')[0]||u?.cookie};}
async function connect(u){const ws=new WebSocket('ws://127.0.0.1:8080/ws',{headers:{Origin:'http://127.0.0.1:5173',Cookie:u.cookie}});sockets.push(ws);await new Promise((r,j)=>{ws.once('message',r);ws.once('error',j)});}
await db.connect();let battle=null;
try{
 const admin=await call('/login',JSON.parse(readFileSync(new URL('../.local/dev.json',import.meta.url),'utf8'))),world=(await call('/world',undefined,admin)).data;
 for(let i=0;i<2;i++){const credentials={username:'hands_'+Date.now().toString(36)+'_'+i,password:randomBytes(16).toString('base64url')};await call('/register',credentials);const u=await call('/login',credentials);users.push(u);await call(`/admin/accounts/${u.data.id}/approval`,{approved:true},admin);await connect(u);}
 const [a,b]=users,profile=async u=>(await call('/character',undefined,u)).data,inventory=async u=>(await call('/inventory',undefined,u)).data;
 for(const code of ['axe','buckler','tower_shield','bandage'])await call(`/admin/accounts/${b.data.id}/items`,{code,quantity:1},admin);
 await call(`/admin/accounts/${a.data.id}/items`,{code:'axe',quantity:1},admin);
 await call('/equipment',{mainHand:'axe',offHand:null},a);await call('/equipment',{mainHand:null,offHand:'buckler'},b);
 assert.equal((await profile(b)).offhand,'buckler');assert.equal((await inventory(b)).find(i=>i.code==='buckler').quantity,0);pass('Single-hand shield equips in offhand and leaves main hand free');
 const occupied=(await db.query('select q,r from accounts where entered=true')).rows,cell=world.tiles.find(t=>t.terrain==='plain'&&!occupied.some(p=>p.q===t.q&&p.r===t.r));
 for(const u of users)await db.query('update accounts set q=$1,r=$2 where id=$3',[cell.q,cell.r,u.data.id]);
 battle=(await call('/battle/start',{targetId:b.data.id,version:world.version},a)).data.id;
 async function fixture(offhand='buckler'){
  await db.query('delete from battle_intents where encounter_id=$1',[battle]);
  await db.query('update battle_actors set initiative=initiative+100 where encounter_id=$1',[battle]);
  await db.query("update characters set life='alive',hp=max_hp,down_hp=max_down_hp,weapon=$1,offhand=null,protected_until=0 where id=$2",['axe',a.data.id]);
  await db.query("update characters set life='alive',hp=max_hp,down_hp=max_down_hp,weapon=null,offhand=$1,protected_until=0 where id=$2",[offhand,b.data.id]);
  await db.query('update battle_actors set q=0,r=0,initiative=0,entry_round=1,reaction_points=0,shield_raised=false where encounter_id=$1 and account_id=$2',[battle,a.data.id]);
  await db.query('update battle_actors set q=1,r=0,initiative=1,entry_round=1,reaction_points=0,shield_raised=false where encounter_id=$1 and account_id=$2',[battle,b.data.id]);
  await db.query('update battle_encounters set turn_account_id=$1,turn_points=6,round_number=1,turn_deadline=$2 where id=$3',[b.data.id,Date.now()+60000,battle]);
 }
 await fixture('buckler');await call('/battle/guard',{battleId:battle},b);let state=(await call('/battle/current',undefined,b)).data;assert.equal(state.turnPoints,4);assert.equal(state.actors.find(x=>x.accountId===b.data.id).reactionPoints,2);
 await db.query('update battle_encounters set turn_account_id=$1,turn_points=6 where id=$2',[a.data.id,battle]);await call('/battle/attack',{battleId:battle,q:1,r:0},a);await db.query('update battle_encounters set turn_account_id=$1,turn_points=6 where id=$2',[b.data.id,battle]);await call('/battle/step',{battleId:battle,q:2,r:0},b);
 assert.equal((await profile(b)).hp,19);state=(await call('/battle/current',undefined,b)).data;assert.equal(state.actors.find(x=>x.accountId===b.data.id).reactionPoints,1);pass('Buckler passive armor and guard reduce a positive hit to at least 1 and consume one reaction');
 await fixture('tower_shield');await call('/battle/guard',{battleId:battle},b);await db.query('update battle_encounters set turn_account_id=$1,turn_points=6 where id=$2',[a.data.id,battle]);await call('/battle/attack',{battleId:battle,q:1,r:0},a);await db.query('update battle_encounters set turn_account_id=$1,turn_points=6 where id=$2',[b.data.id,battle]);await call('/battle/step',{battleId:battle,q:2,r:0},b);
 assert.equal((await profile(b)).hp,20);pass('Two-hand shield can guard remaining 1 damage down to 0');
 await db.query("update characters set hp=10,offhand='buckler' where id=$1",[b.data.id]);await db.query('update battle_encounters set turn_account_id=$1,turn_points=6 where id=$2',[b.data.id,battle]);await call('/battle/bandage',{targetId:b.data.id},b);
 assert.equal((await profile(b)).hp,14);assert.equal((await inventory(b)).find(i=>i.code==='bandage')?.quantity??0,0);state=(await call('/battle/current',undefined,b)).data;assert.equal(state.turnPoints,4);pass('Battle bandage heals standing target by 4 and consumes 2 AP plus one item');
 await call(`/admin/accounts/${b.data.id}/items`,{code:'bandage',quantity:1},admin);await db.query("update characters set hp=12 where id=$1",[b.data.id]);await db.query('delete from battle_encounters where id=$1',[battle]);battle=null;await call('/character/action',{action:'bandage',targetId:b.data.id},b);assert.equal((await profile(b)).hp,16);pass('World bandage heals same-cell standing target without tactical AP');
 writeFileSync(new URL('../.local/hands-shields-report.json',import.meta.url),JSON.stringify({date:new Date().toISOString(),checks},null,2));
}finally{
 for(const ws of sockets)ws.close();if(battle)await db.query('delete from battle_encounters where id=$1',[battle]);
 for(const u of users){await db.query('delete from admin_audit where target like $1',[u.data.id+'%']);await db.query('delete from accounts where id=$1',[u.data.id]);}
 await db.end();
}
