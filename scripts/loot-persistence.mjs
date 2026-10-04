import assert from 'node:assert/strict';
import {readFileSync,writeFileSync} from 'node:fs';
import pg from 'pg';
const root=new URL('../.local/',import.meta.url),fixture=JSON.parse(readFileSync(new URL('loot-fixture.json',root)));
const db=new pg.Client({host:'127.0.0.1',port:55432,user:'worldgame',database:'worldgame'});
await db.connect();
try{
 const ids=fixture.users.map(u=>u.data.id);
 const snapshot=async()=>({world:(await db.query('select document from worlds where id=1')).rows,carried:(await db.query('select * from loot_containers where carrier_id=any($1::uuid[]) order by id',[ids])).rows,ground:(await db.query('select * from loot_containers where q=$1 and r=$2 and carrier_id is null order by id',[fixture.cell.q,fixture.cell.r])).rows,items:(await db.query('select i.* from loot_items i join loot_containers c on c.id=i.container_id where c.carrier_id=any($1::uuid[]) or (c.q=$2 and c.r=$3) order by i.container_id,i.item_code',[ids,fixture.cell.q,fixture.cell.r])).rows});
 if(process.argv.includes('--capture')){const s=await snapshot();assert(s.carried.length);assert(s.ground.length);writeFileSync(new URL('loot-persistence.json',root),JSON.stringify(s));console.log('Captured world, carried corpses, ground deadlines and contents');}
 else if(process.argv.includes('--cleanup')){
  await db.query('begin');
  const s=JSON.parse(readFileSync(new URL('loot-persistence.json',root))),containers=[...fixture.containers,...s.ground.map(c=>c.id),...s.carried.map(c=>c.id)];
  await db.query('delete from loot_containers where id=any($1::uuid[])',[containers]);
  for(const id of fixture.battles)await db.query('delete from battle_encounters where id=$1',[id]);
  for(const id of fixture.monsters)await db.query('delete from characters where id=$1',[id]);
  for(const id of ids){await db.query('delete from admin_audit where target like $1',[id+'%']);await db.query('delete from accounts where id=$1',[id]);}
  await db.query('commit');console.log('Removed only recorded disposable fixture instances');
 }else{assert.deepEqual(await snapshot(),JSON.parse(readFileSync(new URL('loot-persistence.json',root))));console.log('PASS restart preserves world, separate carried corpses, absolute ground deadlines and contents');}
}finally{await db.end();}
