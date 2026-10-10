// Only uses the isolated local DB. Exact fixture IDs are cleaned even after a failed assertion.
import pg from 'pg';
import {WebSocket} from 'ws';
import {randomUUID} from 'node:crypto';
import assert from 'node:assert/strict';
const db=new pg.Client({host:'127.0.0.1',port:55432,user:'worldgame',database:'worldgame'});
const battle=randomUUID(),users=[],sockets=[];
let a,b;
async function call(user,path,body,status=200){
 const response=await fetch('http://127.0.0.1:8080/api'+path,{method:body?'POST':'GET',headers:{Origin:'http://127.0.0.1:5173','X-World-Request':'1','Content-Type':'application/json',Cookie:user?.cookie||''},body:body?JSON.stringify(body):undefined});
 const data=await response.json();assert.equal(response.status,status,`${path}: ${JSON.stringify(data)}`);return {data,cookie:response.headers.get('set-cookie')?.split(';')[0]};
}
async function query(sql,args=[]){return (await db.query(sql,args)).rows;}
async function turn(user,points=6){await query('update battle_encounters set turn_account_id=$1,turn_points=$2,turn_deadline=$3 where id=$4',[user.data.id,points,Date.now()+600000,battle]);}
async function ap(){return (await query('select turn_points from battle_encounters where id=$1',[battle]))[0].turn_points;}
async function ammo(){return Number((await query("select quantity from inventories where character_id=$1 and item_code='arrow'",[a.data.id]))[0].quantity);}
async function intent(){return (await query('select * from battle_intents where encounter_id=$1 and attacker_id=$2',[battle,a.data.id]))[0];}
async function action(id,target=b.data.id,extra={},status=200){return call(a,'/battle/action',{battleId:battle,actionId:id,targetId:target,...extra},status);}
await db.connect();
try{
 for(let i=0;i<3;i++){
  const credentials={username:'rangedqa_'+Date.now()+'_'+i,password:randomUUID()};await call(null,'/register',credentials);const u=await call(null,'/login',credentials);users.push(u);
  await query('update accounts set approved=true,entered=true,admin=$1 where id=$2',[i===0,u.data.id]);
  const ws=new WebSocket('ws://127.0.0.1:8080/ws',{headers:{Origin:'http://127.0.0.1:5173',Cookie:u.cookie}});sockets.push(ws);await new Promise((r,j)=>{ws.once('message',r);ws.once('error',j);});
 }
 [a,b]=users;
 const raw=(await query('select document from worlds where id=1'))[0].document,world=typeof raw==='string'?JSON.parse(raw):raw;
 const occupied=new Set((await query('select world_q,world_r from battle_encounters where active')).map(x=>x.world_q+','+x.world_r));
 const cell=world.tiles.find(t=>t.terrain==='plain'&&!occupied.has(t.q+','+t.r));
 await query('begin');
 await query("update characters set hp=20,max_hp=20,life='alive',weapon='short_bow',protected_until=0 where id=$1",[a.data.id]);
 await query("update characters set hp=100,max_hp=100,life='alive',protected_until=0,body='light_armor' where id=$1",[b.data.id]);
 await query("insert into inventories(character_id,item_code,quantity) values($1,'arrow',12),($1,'healing_potion',2),($1,'stone',2),($1,'light_armor',1)",[a.data.id]);
 await query('insert into battle_encounters(id,world_version,world_q,world_r,turn_account_id,turn_deadline) values($1,$2,$3,$4,$5,$6)',[battle,world.version,cell.q,cell.r,a.data.id,Date.now()+600000]);
 await query('insert into battle_actors(encounter_id,account_id,q,r,initiative,entry_round) values($1,$2,0,0,0,1),($1,$3,3,0,1,1)',[battle,a.data.id,b.data.id]);
 await query('insert into battle_members values($1,$2),($1,$3)',[battle,a.data.id,b.data.id]);await query('commit');
 await action('aim',null,{q:3,r:0});assert.equal(await ap(),4);assert.equal(await ammo(),12);assert.equal((await intent()).ready,false);
 await action('aimed-shot',b.data.id,{},400);assert.equal(await ap(),4);assert.equal(await ammo(),12);
 await action('quick-shot');assert.equal(await ap(),2);assert.equal(await ammo(),11);assert(await intent());
 assert.equal((await query('select hp from characters where id=$1',[b.data.id]))[0].hp,98);
 console.log('PASS aim spends AP but reserves no ammo; premature release rejected; quick shot preserves aim, applies armor');
 await call(a,'/battle/end-turn',{battleId:battle});await call(b,'/battle/end-turn',{battleId:battle});assert.equal((await intent()).ready,true);assert.equal(await ap(),6);
 await query("update inventories set quantity=0 where character_id=$1 and item_code='arrow'",[a.data.id]);
 await action('aimed-shot',b.data.id,{},400);assert(await intent());assert.equal(await ap(),6);
 await query("update inventories set quantity=10 where character_id=$1 and item_code='arrow'",[a.data.id]);
 await action('aimed-shot');assert.equal(await ap(),6);assert.equal(await ammo(),9);assert.equal(await intent(),undefined);
 console.log('PASS ready on next own turn; missing arrows neither consumes AP nor clears aim; release costs zero AP');
 await action('aim',null,{q:3,r:0});await call(a,'/battle/step',{battleId:battle,q:0,r:1});assert.equal(await intent(),undefined);assert.equal(await ammo(),9);
 await turn(a);await action('aim',null,{q:3,r:0});await call(a,'/battle/end-turn',{battleId:battle});
 await call(b,'/battle/step',{battleId:battle,q:4,r:0});assert(await intent());
 await call(b,'/battle/end-turn',{battleId:battle});await call(a,'/battle/end-turn',{battleId:battle});assert.equal(await intent(),undefined);
 console.log('PASS shooter movement cancels; target movement does not trigger; unused ready aim expires');
 await turn(a);await query('update battle_actors set q=1,r=1 where encounter_id=$1 and account_id=$2',[battle,b.data.id]);
 await action('skill:interrupt',b.data.id,{},400);assert.equal(await ap(),6);
 await call(a,`/admin/characters/${a.data.id}/skills`,{code:'interrupt',granted:true});
 await action('skill:interrupt',b.data.id,{},400);assert.equal(await ap(),6);
 await query("insert into battle_intents(encounter_id,attacker_id,q,r,visibility,intent_kind,ready) values($1,$2,0,1,'public','aim',true)",[battle,b.data.id]);
 await action('skill:interrupt');assert.equal(await ap(),4);assert.equal((await query('select * from battle_intents where encounter_id=$1 and attacker_id=$2',[battle,b.data.id])).length,0);
 await call(b,`/admin/characters/${a.data.id}/skills`,{code:'interrupt',granted:false},403);
 assert.equal((await call(a,`/admin/characters/${b.data.id}/skills`)).data.length,0);
 console.log('PASS interrupt is individually learned, requires intent, spends two AP, admin protected');
 await query('update characters set hp=13 where id=$1',[a.data.id]);await action('potion',a.data.id);assert.equal(await ap(),3);assert.equal((await query('select hp from characters where id=$1',[a.data.id]))[0].hp,19);
 await action('throw-stone');assert.equal(await ap(),1);
 await query('update characters set hp=max_hp where id=$1',[a.data.id]);await action('potion',a.data.id,{},400);assert.equal(await ap(),1);
 console.log('PASS self potion heals six for one AP; throwing spends stone and two AP; full-health use rejected');
 await turn(a);await call(a,'/battle/equipment',{mainHand:'short_bow',offHand:null,body:'light_armor',updateBody:true});assert.equal(await ap(),4);
 assert.equal((await query('select body from characters where id=$1',[a.data.id]))[0].body,'light_armor');
 await call(a,'/battle/equipment',{mainHand:'short_bow',offHand:null});assert.equal((await query('select body from characters where id=$1',[a.data.id]))[0].body,'light_armor');
 const report=(await call(a,'/battle/report')).data;assert(report.events.some(e=>e.kind==='attack'&&e.amount===2&&e.blocked===1));assert(report.events.some(e=>e.kind==='potion'&&e.amount===6));
 assert.equal((await call(users[2],'/battle/report')).data.events.length,0);
 console.log('PASS body equip shared cost and legacy compatibility; report numerical feedback and viewer isolation');
 await query("update characters set life='down',hp=0 where id=$1",[b.data.id]);await call(b,'/character/action',{action:'surrender'});
 const saved=(await call(a,'/battle/report')).data;assert.equal(saved.active,false);assert(saved.events.some(e=>e.kind==='close'));
 console.log('PASS latest completed report retained after battle closes');
}finally{
 sockets.forEach(s=>s.close());await query('rollback');await query('delete from loot_containers where battle_id=$1',[battle]);await query('delete from battle_encounters where id=$1',[battle]);
 for(const u of users){await query('delete from admin_audit where actor=$1',[u.data.id]);await query('delete from accounts where id=$1 and username like $2',[u.data.id,'rangedqa_%']);}await db.end();
}
