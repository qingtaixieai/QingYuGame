// Local PostgreSQL only. Tests the real scheduler and command validation; cleans exact fixture IDs.
import pg from 'pg';
import {WebSocket} from 'ws';
import {randomUUID} from 'node:crypto';
import assert from 'node:assert/strict';
const db=new pg.Client({host:'127.0.0.1',port:55432,user:'worldgame',database:'worldgame'});
const npc=randomUUID(),battle=randomUUID();let user,ws;
const pause=ms=>new Promise(r=>setTimeout(r,ms));
async function call(path,body,status=200){const r=await fetch('http://127.0.0.1:8080/api'+path,{method:body?'POST':'GET',headers:{Origin:'http://127.0.0.1:5173','X-World-Request':'1','Content-Type':'application/json',Cookie:user?.cookie||''},body:body?JSON.stringify(body):undefined});const data=await r.json();assert.equal(r.status,status,JSON.stringify(data));return {data,cookie:r.headers.get('set-cookie')?.split(';')[0]};}
async function until(check){for(let i=0;i<40;i++){if(await check())return;await pause(100);}throw Error('Scheduler did not progress');}
await db.connect();
try{
 const credentials={username:'npcqa_'+Date.now(),password:randomUUID()};await call('/register',credentials);user=await call('/login',credentials);
 const raw=(await db.query('select document from worlds where id=1')).rows[0].document,world=typeof raw==='string'?JSON.parse(raw):raw;
 const occupied=new Set((await db.query('select world_q,world_r from battle_encounters where active')).rows.map(x=>x.world_q+','+x.world_r));
 const cell=world.tiles.find(t=>t.terrain==='plain'&&!occupied.has(t.q+','+t.r));
 await db.query('update accounts set approved=true,entered=true,q=$1,r=$2 where id=$3',[cell.q,cell.r,user.data.id]);
 ws=new WebSocket('ws://127.0.0.1:8080/ws',{headers:{Origin:'http://127.0.0.1:5173',Cookie:user.cookie}});await new Promise((r,j)=>{ws.once('message',r);ws.once('error',j)});
 await db.query('begin');
 await db.query("update characters set weapon='axe',hp=max_hp,protected_until=0 where id=$1",[user.data.id]);
 await db.query("insert into characters(id,kind,species,name,q,r,bind_q,bind_r,weapon,deployed,next_move) values($1,'monster','bully','NPC回归测试',$2,$3,$2,$3,'axe',true,$4)",[npc,cell.q,cell.r,Date.now()+3600000]);
 await db.query('insert into battle_encounters(id,world_version,world_q,world_r,turn_account_id,turn_deadline) values($1,$2,$3,$4,$5,$6)',[battle,world.version,cell.q,cell.r,user.data.id,Date.now()+60000]);
 await db.query('insert into battle_actors(encounter_id,account_id,q,r,initiative,entry_round) values($1,$2,6,0,0,1),($1,$3,6,-1,1,1)',[battle,user.data.id,npc]);await db.query('commit');
 await call('/battle/attack',{battleId:battle,q:6,r:-1},400);
 assert.equal((await db.query('select turn_points from battle_encounters where id=$1',[battle])).rows[0].turn_points,6);
 console.log('PASS player partial edge fan rejected without AP cost');
 // Swap cells safely, then let the NPC choose a legal alternative fan hitting the player.
 await db.query('begin');await db.query('update battle_actors set q=0,r=0 where encounter_id=$1 and account_id=$2',[battle,user.data.id]);await db.query('update battle_actors set q=6,r=0 where encounter_id=$1 and account_id=$2',[battle,npc]);await db.query('update battle_actors set q=6,r=-1 where encounter_id=$1 and account_id=$2',[battle,user.data.id]);await db.query('update battle_encounters set turn_account_id=$1,turn_points=2,turn_deadline=$2 where id=$3',[npc,Date.now()+60000,battle]);await db.query('commit');
 await until(async()=>Number((await db.query("select count(*) n from battle_events where encounter_id=$1 and actor_id=$2 and kind='mark'",[battle,npc])).rows[0].n)>0);
 const intents=(await db.query('select cells from battle_intents where encounter_id=$1 and attacker_id=$2',[battle,npc])).rows;
 assert.equal(intents.length,1);for(const h of JSON.parse(intents[0].cells))assert(Math.max(Math.abs(h.q),Math.abs(h.r),Math.abs(h.q+h.r))<=6);
 console.log('PASS NPC uses legal complete fan at edge and ends spent turn');
 await db.query('begin');await db.query('delete from battle_intents where encounter_id=$1',[battle]);await db.query('update characters set hp=1 where id=$1',[npc]);await db.query("insert into inventories(character_id,item_code,quantity) values($1,'bandage',1)",[npc]);await db.query('update battle_encounters set turn_account_id=$1,turn_points=2,turn_deadline=$2 where id=$3',[npc,Date.now()+60000,battle]);await db.query('commit');
 await until(async()=>Number((await db.query("select count(*) n from battle_events where encounter_id=$1 and actor_id=$2 and kind='bandage'",[battle,npc])).rows[0].n)>0);
 assert.equal((await db.query('select hp from characters where id=$1',[npc])).rows[0].hp,5);
 assert.equal(Number((await db.query("select coalesce((select quantity from inventories where character_id=$1 and item_code='bandage'),0) n",[npc])).rows[0].n),0);
 assert.equal((await db.query('select turn_account_id from battle_encounters where id=$1',[battle])).rows[0].turn_account_id,user.data.id);
 console.log('PASS NPC shares bandage handler: consumes one, heals four, spends two AP');
 await db.query('begin');await db.query('delete from battle_intents where encounter_id=$1',[battle]);await db.query("delete from battle_events where encounter_id=$1",[battle]);await db.query('update battle_encounters set turn_account_id=$1,turn_points=6,turn_deadline=$2 where id=$3',[npc,Date.now()-10000,battle]);await db.query('commit');
 await until(async()=>(await db.query('select turn_account_id from battle_encounters where id=$1',[battle])).rows[0].turn_account_id===user.data.id);
 assert.equal(Number((await db.query("select count(*) n from battle_events where encounter_id=$1 and actor_id=$2 and kind in ('mark','move','bandage')",[battle,npc])).rows[0].n),0);
 console.log('PASS expired NPC turn advances before any AI action');
 await db.query('update battle_encounters set turn_deadline=$1 where id=$2',[Date.now()-10000,battle]);
 await until(async()=>Number((await db.query("select count(*) n from battle_events where encounter_id=$1",[battle])).rows[0].n)>0);
 console.log('PASS player timeout still advances');
}finally{
 ws?.close();await db.query('rollback');await db.query('delete from loot_containers where source_id=$1 or battle_id=$2',[npc,battle]);
 await db.query('delete from battle_encounters where id=$1',[battle]);await db.query('delete from characters where id=$1',[npc]);if(user)await db.query('delete from accounts where id=$1 and username like $2',[user.data.id,'npcqa_%']);await db.end();
}
