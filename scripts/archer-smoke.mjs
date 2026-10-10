import pg from 'pg';
import {WebSocket} from 'ws';
import {randomUUID} from 'node:crypto';
import assert from 'node:assert/strict';
const db=new pg.Client({host:'127.0.0.1',port:55432,user:'worldgame',database:'worldgame'});
const battle=randomUUID();let user,ws,npc;
const pause=ms=>new Promise(r=>setTimeout(r,ms));
async function q(sql,args=[]){return (await db.query(sql,args)).rows;}
async function call(path,body,status=200){const r=await fetch('http://127.0.0.1:8080/api'+path,{method:body?'POST':'GET',headers:{Origin:'http://127.0.0.1:5173','X-World-Request':'1','Content-Type':'application/json',Cookie:user?.cookie||''},body:body?JSON.stringify(body):undefined});const data=await r.json();assert.equal(r.status,status,JSON.stringify(data));return {data,cookie:r.headers.get('set-cookie')?.split(';')[0]};}
async function until(check){for(let i=0;i<60;i++){if(await check())return;await pause(100);}throw Error('NPC action did not complete');}
await db.connect();
try{
 const creds={username:'archerqa_'+Date.now(),password:randomUUID()};await call('/register',creds);user=await call('/login',creds);
 await q('update accounts set approved=true,entered=true,admin=true where id=$1',[user.data.id]);
 ws=new WebSocket('ws://127.0.0.1:8080/ws',{headers:{Origin:'http://127.0.0.1:5173',Cookie:user.cookie}});await new Promise((r,j)=>{ws.once('message',r);ws.once('error',j);});
 const raw=(await q('select document from worlds where id=1'))[0].document,w=typeof raw==='string'?JSON.parse(raw):raw;
 const characters=(await call('/admin/characters')).data,occupied=new Set(characters.filter(c=>!['soul','dead'].includes(c.life)).map(c=>c.q+','+c.r));
 const encounters=new Set((await q('select world_q,world_r from battle_encounters where active')).map(c=>c.world_q+','+c.world_r));
 const cell=w.tiles.find(t=>t.terrain==='plain'&&!occupied.has(t.q+','+t.r)&&!encounters.has(t.q+','+t.r)&&w.tiles.some(n=>n.q===t.q+1&&n.r===t.r&&!['ocean','river','mountain'].includes(n.terrain)));
 await call('/admin/creatures/deploy',{species:'archer',q:cell.q,r:cell.r});
 npc=(await q("select id from characters where species='archer' and q=$1 and r=$2",[cell.q,cell.r]))[0].id;
 await q('update characters set next_move=$1 where id=$2',[Date.now()+3600000,npc]);
 const c=(await q('select * from characters where id=$1',[npc]))[0];assert.equal(c.hp,12);assert.equal(c.max_down_hp,6);assert.equal(c.weapon,'short_bow');assert.equal(c.body,'light_armor');assert.equal(c.deployed,true);
 assert.equal(Number((await q("select quantity from inventories where character_id=$1 and item_code='arrow'",[npc]))[0].quantity),12);
 console.log('PASS admin-deployed archer has specified HP, bow, armor and ammunition');
 await q('begin');await q("update characters set hp=100,max_hp=100,weapon='short_bow',protected_until=0 where id=$1",[user.data.id]);await q("insert into inventories values($1,'arrow',10)",[user.data.id]);
 await q('insert into battle_encounters(id,world_version,world_q,world_r,turn_account_id,turn_deadline) values($1,$2,$3,$4,$5,$6)',[battle,w.version,cell.q,cell.r,npc,Date.now()+60000]);
 await q('insert into battle_actors(encounter_id,account_id,q,r,initiative,entry_round) values($1,$2,0,0,0,1),($1,$3,3,0,1,1)',[battle,user.data.id,npc]);await q('insert into battle_members values($1,$2),($1,$3)',[battle,user.data.id,npc]);await q('commit');
 await until(async()=>Number((await q("select count(*) n from battle_events where encounter_id=$1 and actor_id=$2 and kind='attack'",[battle,npc]))[0].n)>0);
 await q('update battle_encounters set turn_account_id=$1,turn_points=6,turn_deadline=$2 where id=$3',[user.data.id,Date.now()+60000,battle]);
 const arrows=Number((await q("select quantity from inventories where character_id=$1 and item_code='arrow'",[npc]))[0].quantity);assert(arrows<12);
 const state=(await call('/battle/current')).data;assert.deepEqual(state.actors.find(a=>a.accountId===npc).actions,[]);
 assert((await call('/battle/report')).data.events.some(e=>e.kind==='attack'&&e.actorId===npc&&e.amount===3));
 console.log('PASS archer uses shared projectile execution, consumes carried arrows, hides private actions');
 await q('begin');await q('update characters set hp=3 where id=$1',[npc]);await q('update battle_encounters set turn_account_id=$1,turn_points=1,turn_deadline=$2 where id=$3',[npc,Date.now()+60000,battle]);await q('commit');
 await until(async()=>Number((await q("select count(*) n from battle_events where encounter_id=$1 and actor_id=$2 and kind='potion'",[battle,npc]))[0].n)>0);
 assert.equal((await q('select hp from characters where id=$1',[npc]))[0].hp,9);
 assert.equal(Number((await q("select quantity from inventories where character_id=$1 and item_code='healing_potion'",[npc]))[0].quantity),0);
 console.log('PASS low-health archer uses one carried potion through shared one-AP handler');
 await q('begin');await q('delete from battle_intents where encounter_id=$1',[battle]);await q('update battle_actors set q=6,r=0 where encounter_id=$1 and account_id=$2',[battle,npc]);await q('update battle_actors set q=3,r=0 where encounter_id=$1 and account_id=$2',[battle,user.data.id]);await q("update inventories set quantity=0 where character_id=$1 and item_code='arrow'",[npc]);await q('update battle_encounters set turn_account_id=$1,turn_points=2,turn_start_edge=false,turn_deadline=$2 where id=$3',[npc,Date.now()+60000,battle]);await q('commit');
 await until(async()=>(await q('select withdraw_direction from battle_actors where encounter_id=$1 and account_id=$2',[battle,npc]))[0]?.withdraw_direction===0);
 await call('/battle/action',{battleId:battle,actionId:'quick-shot',targetId:npc});
 assert.equal((await q('select withdraw_direction from battle_actors where encounter_id=$1 and account_id=$2',[battle,npc]))[0].withdraw_direction,null);
 console.log('PASS empty-ammo archer prepares ordinary exit; player attack interrupts withdrawal');
 await q('begin');await q("update inventories set quantity=3 where character_id=$1 and item_code='arrow'",[npc]);await q("update characters set life='down',hp=0,down_hp=1 where id=$1",[npc]);await q('update battle_encounters set turn_account_id=$1,turn_points=6,turn_deadline=$2 where id=$3',[user.data.id,Date.now()+60000,battle]);await q('commit');
 await call('/battle/action',{battleId:battle,actionId:'quick-shot',targetId:npc});
 const corpse=(await q("select id from loot_containers where source_id=$1 and kind='corpse'",[npc]))[0];assert(corpse);
 const loot=Object.fromEntries((await q('select item_code,quantity from loot_items where container_id=$1',[corpse.id])).map(x=>[x.item_code,Number(x.quantity)]));
 assert.equal(loot.arrow,3);assert.equal(loot.short_bow,1);assert.equal(loot.light_armor,1);assert.equal(loot.healing_potion,undefined);
 assert.equal((await q('select * from inventories where character_id=$1',[npc])).length,0);assert.equal((await q('select weapon from characters where id=$1',[npc]))[0].weapon,null);
 console.log('PASS corpse receives only remaining ammo, bow and armor, without spent potion; ownership transferred once');
}finally{
 ws?.close();await q('rollback');if(npc)await q('delete from loot_containers where source_id=$1',[npc]);await q('delete from battle_encounters where id=$1',[battle]);if(npc)await q('delete from characters where id=$1',[npc]);if(user){await q('delete from admin_audit where actor=$1',[user.data.id]);await q('delete from accounts where id=$1',[user.data.id]);}await db.end();
}
