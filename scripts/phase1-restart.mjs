// Test-only persistence checkpoint. Hard-coded dedicated local QA database.
import pg from 'pg';import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';import {readFileSync,writeFileSync} from 'node:fs';
const db=new pg.Client({host:'127.0.0.1',port:55432,user:'worldgame',password:'local-only',database:'qingyu_phase1_browser'});
const file=new URL('../.local/phase1-restart.json',import.meta.url);await db.connect();
async function snapshot(id){
 const encounter=(await db.query('select * from battle_encounters where id=$1',[id])).rows[0];
 const deadline=Number(encounter.turn_deadline);delete encounter.turn_deadline;
 return {deadline,encounter,actors:(await db.query('select * from battle_actors where encounter_id=$1 order by account_id',[id])).rows,events:(await db.query('select * from battle_events where encounter_id=$1 order by id',[id])).rows,portals:(await db.query('select * from scene_portals order by id')).rows};
}
try{
 if(process.argv[2]==='prepare'){
  const ids=(await db.query("select id from accounts where username<>'admin' order by id limit 2")).rows.map(x=>x.id);assert.equal(ids.length,2);
  const raw=(await db.query('select document from worlds where id=1')).rows[0].document;const world=typeof raw==='string'?JSON.parse(raw):raw;
  const id=randomUUID();await db.query('begin');
  await db.query('delete from battle_encounters');
  for(const user of ids)await db.query("update characters set life='alive',hp=max_hp,timer_kind=null,timer_end=0 where id=$1",[user]);
  await db.query('insert into battle_encounters(id,world_version,world_q,world_r,turn_account_id,turn_points,turn_deadline) values($1,$2,$3,$4,$5,2,1)',[id,world.version,world.spawn.q,world.spawn.r,ids[0]]);
  for(let i=0;i<2;i++)await db.query('insert into battle_actors(encounter_id,account_id,q,r,initiative,entry_round) values($1,$2,$3,0,$3,1)',[id,ids[i],i]);
  await db.query('commit');writeFileSync(file,JSON.stringify({id,preparedAt:Date.now(),snapshot:await snapshot(id)}));console.log('Restart checkpoint prepared');
 }else{
  const before=JSON.parse(readFileSync(file,'utf8'));const actual=JSON.parse(JSON.stringify(await snapshot(before.id)));
  assert.ok(actual.deadline>before.preparedAt);delete actual.deadline;delete before.snapshot.deadline;assert.deepEqual(actual,before.snapshot);
  console.log('PASS actual process restart: deadline renewed; AP, turn, actors, events and portals unchanged');
  await db.query('delete from battle_encounters where id=$1',[before.id]);
 }
}finally{await db.end();}
