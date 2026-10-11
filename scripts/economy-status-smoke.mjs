// Local-only database integration. Deletes only this run's exact fixture IDs.
import pg from 'pg';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {WebSocket} from 'ws';
const db=new pg.Client({host:'127.0.0.1',port:55432,user:'worldgame',database:'worldgame'}),users=[],sockets=[],battle=randomUUID(),corpse=randomUUID();
const q=async(sql,args=[]) => (await db.query(sql,args)).rows;
async function call(u,path,body,status=200){const r=await fetch('http://127.0.0.1:8080/api'+path,{method:body?'POST':'GET',headers:{Origin:'http://127.0.0.1:5173','X-World-Request':'1','Content-Type':'application/json',Cookie:u?.cookie??''},body:body?JSON.stringify(body):undefined});const data=await r.json();assert.equal(r.status,status,`${path}: ${JSON.stringify(data)}`);return {data,cookie:r.headers.get('set-cookie')?.split(';')[0]};}
await db.connect();
try{
 for(let i=0;i<2;i++){const credentials={username:'ecoqa_'+Date.now()+'_'+i,password:randomUUID()};await call(null,'/register',credentials);const u=await call(null,'/login',credentials);users.push(u);await q('update accounts set approved=true,entered=true,admin=true where id=$1',[u.data.id]);const ws=new WebSocket('ws://127.0.0.1:8080/ws',{headers:{Origin:'http://127.0.0.1:5173',Cookie:u.cookie}});sockets.push(ws);await new Promise((resolve,reject)=>{ws.once('message',resolve);ws.once('error',reject);});}
 const [a,b]=users,aid=a.data.id,bid=b.data.id;
 const raw=(await q('select document from worlds where id=1'))[0].document,world=typeof raw==='string'?JSON.parse(raw):raw,town=world.tiles.find(t=>t.place?.type==='town');
 await q('update accounts set q=$1,r=$2 where id=any($3::uuid[])',[town.q,town.r,[aid,bid]]);
 let shop=(await call(a,'/shop')).data;assert.equal(Number(shop.coins.copper_coin),30);
 await call(a,'/shop/trade',{mode:'buy',lines:[{code:'arrow',quantity:2}]});shop=(await call(a,'/shop')).data;assert.equal(Number(shop.coins.copper_coin),20);
 await call(a,'/shop/trade',{mode:'buy',lines:[{code:'short_bow',quantity:1}]},400);assert.equal(Number((await call(a,'/shop')).data.coins.copper_coin),20);
 await call(a,'/shop/trade',{mode:'sell',lines:[{code:'arrow',quantity:1}]});assert.equal(Number((await call(a,'/shop')).data.coins.copper_coin),21);
 await call(a,'/shop/trade',{mode:'sell',lines:[{code:'copper_coin',quantity:1}]},400);
 await q("insert into inventories values($1,'silver_coin',1) on conflict(character_id,item_code) do update set quantity=excluded.quantity",[aid]);await call(a,'/shop/trade',{mode:'buy',lines:[{code:'healing_potion',quantity:1}]});shop=(await call(a,'/shop')).data;assert.equal(Number(shop.coins.copper_coin),96);assert.equal(Number(shop.coins.silver_coin),0);
 const hot=(await call(a,'/hotbar')).data,potionSlot=hot.slots.findIndex(x=>x.actionRef==='potion');assert(potionSlot>=0);await call(a,'/hotbar/layout',{revision:hot.revision,operation:'lock',from:potionSlot});await call(a,'/shop/trade',{mode:'sell',lines:[{code:'healing_potion',quantity:1}]},400);
 await q("insert into loot_containers(id,kind,name,world_version,q,r,created_at,carrier_id,source_species) values($1,'corpse','测试空尸体',$2,$3,$4,$5,$6,'bully')",[corpse,world.version,town.q,town.r,Date.now(),aid]);await q("insert into loot_items values($1,'wood',1)",[corpse]);await call(a,'/shop/trade',{mode:'sell',lines:[{corpseId:corpse,quantity:1}]},400);await q('delete from loot_items where container_id=$1',[corpse]);await call(a,'/shop/trade',{mode:'sell',lines:[{corpseId:corpse,quantity:1}]});assert.equal((await q('select id from loot_containers where id=$1',[corpse])).length,0);
 console.log('PASS shop exact prices, physical change, insufficient-funds rollback, fixed item rejection and empty-corpse sale');
 await q("update characters set weapon='short_bow',hp=20,max_hp=20,life='alive',protected_until=0 where id=$1",[aid]);
 await q("update characters set hp=20,max_hp=20,life='alive',protected_until=0 where id=$1",[bid]);
 await q("insert into inventories values($1,'poison_arrow',8),($1,'antidote',2),($2,'antidote',2) on conflict(character_id,item_code) do update set quantity=excluded.quantity",[aid,bid]);
 await q('insert into battle_encounters(id,world_version,world_q,world_r,turn_account_id,turn_deadline) values($1,$2,300,300,$3,$4)',[battle,world.version,aid,Date.now()+600000]);
 await q('insert into battle_actors(encounter_id,account_id,q,r,initiative,entry_round) values($1,$2,0,0,0,1),($1,$3,3,0,1,1)',[battle,aid,bid]);await q('insert into battle_members values($1,$2),($1,$3)',[battle,aid,bid]);
 const turn=async(u)=>q('update battle_encounters set turn_account_id=$1,turn_points=6,turn_deadline=$2 where id=$3',[u.data.id,Date.now()+600000,battle]);
 const fire=async()=>call(a,'/battle/action',{battleId:battle,actionId:'quick-shot',targetId:bid});
 await call(a,'/character/ammo',{code:'poison_arrow'});await fire();let s=(await call(b,'/character/status')).data;assert.equal(s.statuses[0].remaining,3);
 await call(a,'/battle/end-turn',{battleId:battle});s=(await call(b,'/character/status')).data;assert.equal(s.statuses[0].remaining,2);assert.equal((await call(b,'/character')).data.hp,16);
 await turn(a);await fire();assert.equal((await call(b,'/character/status')).data.statuses[0].remaining,3);
 await turn(b);await call(b,'/battle/action',{battleId:battle,actionId:'antidote',targetId:bid});assert.equal((await call(b,'/character/status')).data.statuses.length,0);
 await call(b,'/battle/action',{battleId:battle,actionId:'antidote',targetId:bid},400);
 await call(a,'/shop/trade',{mode:'buy',lines:[{code:'arrow',quantity:1}]},400);
 const state=(await call(a,'/battle/current')).data,enemy=state.actors.find(x=>x.accountId===bid);assert.equal(enemy.character.strength,undefined);assert.equal(enemy.loadout,undefined);assert(state.actors.find(x=>x.accountId===aid).loadout);
 const report=(await call(a,'/battle/report')).data;assert(report.events.some(x=>x.details?.trigger));assert(report.events.some(x=>x.kind==='status-refreshed'));assert(report.events.filter(x=>x.targetId===bid).every(x=>x.details?.armorReduction===undefined));
 console.log('PASS poison applies/refreshes/ticks, antidote consumes through common action, battle shop blocked, enemy data redacted');
 await q("update characters set weapon=null,offhand='tower_shield' where id=$1",[bid]);await q('update battle_actors set shield_raised=true,reaction_points=2 where encounter_id=$1 and account_id=$2',[battle,bid]);await turn(a);await fire();assert.equal((await call(b,'/character/status')).data.statuses.length,0);
 await q("update characters set offhand=null where id=$1",[bid]);await q('update battle_actors set shield_raised=false,reaction_points=0 where encounter_id=$1 and account_id=$2',[battle,bid]);
 await turn(a);await call(a,'/battle/action',{battleId:battle,actionId:'aim',q:3,r:0});await call(a,'/character/ammo',{code:'arrow'});assert.equal((await q('select count(*)::int as n from battle_intents where encounter_id=$1',[battle]))[0].n,1);await call(a,'/character/ammo',{code:'poison_arrow'});await call(a,'/battle/end-turn',{battleId:battle});await call(b,'/battle/end-turn',{battleId:battle});await call(a,'/battle/action',{battleId:battle,actionId:'aimed-shot',targetId:bid});assert.equal((await call(b,'/character/status')).data.statuses[0].remaining,3);
 // Temporarily suspend only this fixture encounter to verify world pause and persistence.
 await q('update battle_encounters set active=false where id=$1',[battle]);let paused=(await call(b,'/character/status')).data;assert.equal(paused.paused,true);assert.equal(paused.statuses[0].remaining,3);await q('update battle_encounters set active=true where id=$1',[battle]);
 console.log('PASS tower full block prevents poison; aim uses release-time ammo; poison persists while outside active battle');
 await turn(a);await fire();await q("update characters set life='down',hp=0,down_hp=1 where id=$1",[bid]);await call(a,'/battle/end-turn',{battleId:battle});assert.equal((await call(b,'/character')).data.life,'soul');assert.equal((await call(b,'/character/status')).data.statuses.length,0);assert.equal((await call(a,'/battle/current')).data.active,false);
 console.log('PASS poison kills downed character and closes battle without stuck turn');
}finally{for(const s of sockets)s.close();await q('rollback');await q('delete from battle_encounters where id=$1',[battle]);await q('delete from loot_containers where id=$1',[corpse]);for(const u of users){await q('delete from admin_audit where actor=$1',[u.data.id]);await q('delete from accounts where id=$1 and username like $2',[u.data.id,'ecoqa_%']);}await db.end();}
