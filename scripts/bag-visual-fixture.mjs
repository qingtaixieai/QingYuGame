import pg from 'pg';
import {readFileSync} from 'node:fs';
const fixture=JSON.parse(readFileSync(new URL('../.local/battle-visual.json',import.meta.url),'utf8'));
if(!fixture.users.every(u=>u.credentials.username.startsWith('visual_')))throw Error('Only disposable visual accounts may be changed');
const db=new pg.Client({host:'127.0.0.1',port:55432,user:'worldgame',database:'worldgame'});
await db.connect();
try{
 await db.query('begin');
 if(process.argv[2]==='cleanup'){
  await db.query('delete from battle_encounters where id=$1',[fixture.battle]);
  for(const u of fixture.users){await db.query('delete from admin_audit where target like $1',[u.data.id+'%']);await db.query('delete from accounts where id=$1 and username=$2',[u.data.id,u.credentials.username]);}
 }else{
  const a=fixture.users[0].data.id;
  await db.query('delete from battle_intents where encounter_id=$1',[fixture.battle]);
  await db.query('update battle_encounters set turn_account_id=$1,turn_points=6,turn_deadline=$2 where id=$3',[a,Date.now()+3600000,fixture.battle]);
  await db.query("update characters set weapon='axe',offhand='buckler' where id=$1",[a]);
  for(const code of ['axe','spear','flail','buckler','tower_shield','bandage'])await db.query('insert into inventories(account_id,item_code,quantity) values($1,$2,$3) on conflict(account_id,item_code) do update set quantity=excluded.quantity',[a,code,code==='bandage'?3:1]);
  const cells=[[0,0],[1,0],[0,1]];
  for(let i=0;i<fixture.users.length;i++){
   await db.query("update characters set life='alive',hp=$1,protected_until=0 where id=$2",[i===2?20:10,fixture.users[i].data.id]);
   await db.query('update battle_actors set q=$1,r=$2,entry_round=1,reaction_points=0,shield_raised=false where encounter_id=$3 and account_id=$4',[...cells[i],fixture.battle,fixture.users[i].data.id]);
  }
 }
 await db.query('commit');console.log(process.argv[2]==='cleanup'?'Visual fixture removed':'Battle bag fixture prepared');
}catch(e){await db.query('rollback');throw e;}finally{await db.end();}
