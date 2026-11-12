import pg from 'pg';
import {randomUUID} from 'node:crypto';
const db=new pg.Client({host:'127.0.0.1',port:55432,user:'worldgame',password:'local-only',database:'qingyu_phase1_browser'});
await db.connect();
try{
 await db.query('begin');
 await db.query('delete from battle_encounters');
 await db.query('delete from scene_portals');await db.query('delete from scene_documents');
 await db.query("update accounts set entered=false where username<>'admin'");
 const raw=(await db.query('select document from worlds where id=1')).rows[0].document;
 const world=typeof raw==='string'?JSON.parse(raw):raw;
 const a=world.spawn,b={q:a.q+1,r:a.r};
 const scenes=[a,b].map(owner=>({id:randomUUID(),worldVersion:world.version,generationVersion:1,level:2,owner,name:'QA',radius:2,cells:[{q:0,r:0,terrain:'grass',walkable:true}]}));
 for(const s of scenes)await db.query('insert into scene_documents values($1,$2,$3)',[s.id,world.version,JSON.stringify(s)]);
 const portal={id:randomUUID(),first:{sceneId:scenes[0].id,cell:{q:0,r:0}},second:{sceneId:scenes[1].id,cell:{q:0,r:0}},open:false};
 await db.query('insert into scene_portals values($1,$2,$3,$4)',[portal.id,scenes[0].id,scenes[1].id,JSON.stringify(portal)]);
 await db.query("update accounts set q=$1,r=$2 where username='admin'",[a.q,a.r]);
 await db.query('commit');console.log('Public closed-edge fixture prepared in dedicated browser database');
}catch(e){await db.query('rollback');throw e;}finally{await db.end();}
