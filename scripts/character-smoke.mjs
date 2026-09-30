import assert from 'node:assert/strict';
import {readFileSync,writeFileSync} from 'node:fs';
import {randomBytes} from 'node:crypto';
import {WebSocket} from 'ws';
import pg from 'pg';
const db=new pg.Client({host:'127.0.0.1',port:55432,user:'worldgame',database:'worldgame'}),users=[],sockets=[],checks=[],battles=[];
const pause=ms=>new Promise(r=>setTimeout(r,ms)),monster='a3347a88-6099-42bf-9d66-000000000001';
const pass=s=>{checks.push(s);console.log('PASS '+s);};
async function call(path,body,u,status=200){const r=await fetch('http://127.0.0.1:8080/api'+path,{method:body===undefined?'GET':'POST',headers:{Origin:'http://127.0.0.1:5173',Cookie:u?.cookie||'','X-World-Request':'1','Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});const data=await r.json();assert.equal(r.status,status,path+': '+JSON.stringify(data));return {data,cookie:r.headers.get('set-cookie')?.split(';')[0]||u?.cookie};}
async function connect(u){const ws=new WebSocket('ws://127.0.0.1:8080/ws',{headers:{Origin:'http://127.0.0.1:5173',Cookie:u.cookie}});sockets.push(ws);await new Promise((r,j)=>{ws.once('message',r);ws.once('error',j)});}
await db.connect();let oldMonster;
try{
 const admin=await call('/login',JSON.parse(readFileSync(new URL('../.local/dev.json',import.meta.url),'utf8'))),world=(await call('/world',undefined,admin)).data;
 oldMonster=(await db.query('select * from characters where id=$1',[monster])).rows[0];
 assert.equal((await db.query('select count(*) from battle_actors where account_id=$1',[monster])).rows[0].count,'0','Monster must be idle before isolated test');
 await db.query('update characters set next_move=$1 where id=$2',[Date.now()+3600000,monster]);
 for(let i=0;i<3;i++){const credentials={username:'life_'+Date.now().toString(36)+'_'+i,password:randomBytes(16).toString('base64url')};await call('/register',credentials);const u=await call('/login',credentials);users.push(u);await call(`/admin/accounts/${u.data.id}/approval`,{approved:true},admin);await connect(u);}
 const [a,b,c]=users,profile=async u=>(await call('/character',undefined,u)).data;
 assert.equal((await profile(a)).strength,10);assert.equal((await profile(a)).hp,20);assert.equal((await profile(a)).bindQ,world.spawn.q);
 await call(`/admin/accounts/${b.data.id}/items`,{code:'flail',quantity:1},a,403);pass('Six base attributes and health initialized; admin grant authorization enforced');
 for(const code of ['flail','axe','spear'])await call(`/admin/accounts/${a.data.id}/items`,{code,quantity:1},admin);
 await call('/equipment',{code:'spear'},a);assert.equal((await profile(a)).weapon,'spear');await call('/equipment',{code:'axe'},a);assert.equal((await call('/inventory',undefined,a)).data.find(i=>i.code==='spear').quantity,1);
 await call('/equipment',{code:'spear'},b,400);pass('Grant, equip, unequip ownership and inventory conservation');
 const occupied=(await db.query('select q,r from accounts where entered=true')).rows;const cell=world.tiles.find(t=>t.terrain==='plain'&&!occupied.some(p=>p.q===t.q&&p.r===t.r));
 for(const u of users)await db.query('update accounts set q=$1,r=$2 where id=$3',[cell.q,cell.r,u.data.id]);
 let battle=(await call('/battle/start',{targetId:b.data.id,version:world.version},a)).data.id;battles.push(battle);
 let state=(await call('/battle/current',undefined,a)).data;assert.equal(state.actors.length,3);assert(state.actors.every(x=>x.initiativeRoll>=1&&x.initiativeRoll<=20));assert(state.actors.every((x,i)=>i===0||state.actors[i-1].initiativeScore>=x.initiativeScore));
 await call('/equipment',{code:'flail'},a,400);pass('One initiative roll per participant; battle equipment locked');
 async function fixture(weapon,positions=[[0,0],[1,0],[0,1]]){
  await db.query('delete from battle_intents where encounter_id=$1',[battle]);await db.query('update battle_actors set q=q+100,r=r+100,initiative=initiative+1000 where encounter_id=$1',[battle]);
  for(let i=0;i<users.length;i++){await db.query("update characters set life='alive',hp=max_hp,down_hp=max_down_hp,protected_until=0 where id=$1",[users[i].data.id]);await db.query('update battle_actors set q=$1,r=$2,initiative=$3,entry_round=1 where encounter_id=$4 and account_id=$5',[...positions[i],i,battle,users[i].data.id]);}
  await db.query('update characters set weapon=$1 where id=$2',[weapon,a.data.id]);await turn(a);
 }
 async function turn(u,points=6){await db.query('update battle_encounters set turn_account_id=$1,turn_points=$2,turn_deadline=$3 where id=$4',[u.data.id,points,Date.now()+60000,battle]);}
 const mark=(q,r)=>call('/battle/attack',{battleId:battle,q,r},a),move=(u,q,r,status=200)=>call('/battle/step',{battleId:battle,q,r},u,status),current=async()=>(await call('/battle/current',undefined,a)).data;
 await fixture('axe');await mark(1,0);assert.equal((await current()).intents[0].cells.length,2);await turn(b);await move(b,2,0);assert.equal((await profile(b)).hp,17);assert.equal((await profile(c)).hp,17);assert.equal((await current()).intents.length,0);pass('Axe departure triggers whole fan exactly once and clears all cells');
 await fixture('axe');await mark(1,0);await mark(0,1);assert.equal((await current()).turnPoints,2);assert.equal((await current()).intents.length,1);await turn(a,1);await call('/battle/attack',{battleId:battle,q:-1,r:1},a,400);assert.equal((await current()).intents[0].q,0);pass('Replacing a multi-cell attack charges again and insufficient AP preserves prior attack');
 await fixture('spear',[[0,0],[1,0],[2,0]]);await mark(2,0);assert.equal((await current()).intents[0].cells.length,2);await move(a,-1,0);assert.equal((await current()).intents.length,1);await move(a,-1,1);assert.equal((await profile(b)).hp,17);assert.equal((await profile(c)).hp,17);pass('Spear retains axial overlap; sideways movement resolves original entire attack');
 await fixture('flail',[[0,0],[2,0],[-4,0]]);await call('/battle/attack',{battleId:battle,q:1,r:0},a,400);await mark(2,0);await move(a,1,0);assert.equal((await profile(b)).hp,15);pass('Flail second ring only; moving into blind first ring resolves damage');
 await fixture(null,[[0,0],[1,0],[-4,0]]);await db.query('update characters set hp=1 where id=$1',[b.data.id]);await mark(1,0);await turn(b);await move(b,2,0);assert.equal((await profile(b)).life,'down');assert.equal((await current()).actors.find(x=>x.accountId===b.data.id).q,1);assert.equal((await profile(b)).downHp,10);pass('Lethal departure damage downs and interrupts movement without overflow');
 await turn(a,5);await call('/character/action',{action:'rescue',targetId:b.data.id},a,400);await turn(a);await call('/character/action',{action:'rescue',targetId:b.data.id},a);assert.equal((await profile(b)).hp,5);assert.equal((await profile(b)).downHp,10);assert.notEqual((await current()).turnAccountId,a.data.id);pass('Rescue requires full turn; restores 5 HP and consumes rescuer turn');
 await fixture('spear',[[0,0],[1,0],[2,0]]);await db.query("update characters set hp=0,life='down',down_hp=2 where id=$1",[b.data.id]);await mark(1,0);await move(a,-2,0);assert.equal((await profile(b)).life,'soul');assert.equal((await profile(b)).q,world.spawn.q);assert.equal((await profile(c)).hp,17);assert(!(await current()).actors.some(x=>x.accountId===b.data.id));
 const soulState=(await call('/players',undefined,b)).data;assert.equal(soulState.players.length,1);assert.equal(soulState.resources.length,0);assert.equal(soulState.ferry,null);await call('/collect',{q:world.spawn.q,r:world.spawn.r,version:world.version},b,400);pass('True death records fixed marker, removes actor and hides living intelligence');
 // Return to marker while the encounter is still active: joins at edge, waits next round.
 await db.query('update accounts set q=$1,r=$2 where id=$3',[cell.q,cell.r,b.data.id]);await call('/character/action',{action:'revive'},b);let returned=(await call('/battle/current',undefined,b)).data;const entrant=returned.actors.find(x=>x.accountId===b.data.id);assert.equal(entrant.entryRound,returned.round+1);assert.equal(Math.max(Math.abs(entrant.q),Math.abs(entrant.r),Math.abs(entrant.q+entrant.r)),returned.radius);await call('/character/action',{action:'surrender'},b);pass('Marker revival enters an active encounter through outer ring on next round');
 await call('/character/action',{action:'recall'},b);const first=(await profile(b)).timerEnd;const adjacent=world.tiles.find(t=>t.q===world.spawn.q+1&&t.r===world.spawn.r&& !['ocean','river','mountain'].includes(t.terrain))||world.tiles.find(t=>Math.max(Math.abs(t.q-world.spawn.q),Math.abs(t.r-world.spawn.r),Math.abs(t.q+t.r-world.spawn.q-world.spawn.r))===1&&!['ocean','river','mountain'].includes(t.terrain));
 await call('/move',{q:adjacent.q,r:adjacent.r,version:world.version},b);await pause(500);assert.equal((await profile(b)).timerEnd,0);await call('/stop',{},b);await call('/character/action',{action:'recall'},b);assert((await profile(b)).timerEnd>first);await pause(5500);assert.equal((await profile(b)).life,'alive');assert.equal((await profile(b)).hp,20);assert.equal((await profile(b)).downHp,10);pass('Recall movement cancels; retry restarts timer and restores full health at binding');
 await call('/character/action',{action:'bind'},b);await db.query('update characters set hp=3,down_hp=2 where id=$1',[b.data.id]);await call('/character/action',{action:'rest'},b);await db.query('update characters set timer_end=$1 where id=$2',[Date.now()-1,b.data.id]);await pause(500);assert.equal((await profile(b)).hp,20);assert.equal((await profile(b)).downHp,10);pass('Town binding and rest restore both health pools');
 // Isolate NPC encounter in its own patch; save and restore the creature afterward.
 await db.query('delete from battle_encounters where id=$1',[battle]);
 for(const u of users)await db.query('update accounts set q=$1,r=$2 where id=$3',[world.spawn.q,world.spawn.r,u.data.id]);
 await db.query("update characters set life='alive',hp=12,down_hp=6,protected_until=0,next_move=$1 where id=$2",[Date.now()+3600000,monster]);const m=(await db.query('select q,r from characters where id=$1',[monster])).rows[0];await db.query('update accounts set q=$1,r=$2 where id=$3',[m.q,m.r,a.data.id]);await pause(800);state=(await current());assert(state.active&&state.actors.some(x=>x.accountId===monster));battle=state.id;battles.push(battle);
 await db.query('update battle_actors set q=q+100,r=r+100 where encounter_id=$1',[battle]);await db.query('update battle_actors set q=0,r=0 where encounter_id=$1 and account_id=$2',[battle,monster]);await db.query('update battle_actors set q=1,r=0 where encounter_id=$1 and account_id=$2',[battle,a.data.id]);await db.query('update battle_encounters set turn_account_id=$1,turn_points=6 where id=$2',[monster,battle]);await pause(3300);
 assert((await profile(a)).hp<20);const events=(await db.query('select kind from battle_events where encounter_id=$1',[battle])).rows;assert(events.some(e=>e.kind==='attack')&&events.some(e=>e.kind==='move'));pass('Aggressive monster encounter and legal mark/move-trigger AI damage');
 await db.query('delete from battle_encounters where id=$1',[battle]);await db.query('update accounts set q=$1,r=$2 where id=$3',[world.spawn.q,world.spawn.r,a.data.id]);
 await db.query("update characters set life='dead',hp=0,down_hp=0,timer_kind='respawn',timer_end=$1 where id=$2",[Date.now()+500,monster]);await pause(1200);const respawn=(await db.query('select life,hp,down_hp,q,r from characters where id=$1',[monster])).rows[0];assert.equal(respawn.life,'alive');assert.equal(respawn.hp,12);assert.equal(respawn.down_hp,6);const dock=world.tiles.find(t=>t.place?.type==='landing');assert(Math.max(Math.abs(respawn.q-dock.q),Math.abs(respawn.r-dock.r),Math.abs(respawn.q+respawn.r-dock.q-dock.r))>1);pass('Monster respawns alive outside dock safety ring without dropping items');
 writeFileSync(new URL('../.local/character-report.json',import.meta.url),JSON.stringify({date:new Date().toISOString(),checks},null,2));
}finally{
 for(const ws of sockets)ws.close();await pause(200);
 for(const id of battles)await db.query('delete from battle_encounters where id=$1',[id]);
 for(const u of users){await db.query('delete from admin_audit where target like $1',[u.data.id+'%']);await db.query('delete from accounts where id=$1',[u.data.id]);}
 if(oldMonster)await db.query('update characters set q=$1,r=$2,hp=$3,down_hp=$4,life=$5,timer_kind=$6,timer_end=$7,next_move=$8,protected_until=$9 where id=$10',[oldMonster.q,oldMonster.r,oldMonster.hp,oldMonster.down_hp,oldMonster.life,oldMonster.timer_kind,oldMonster.timer_end,oldMonster.next_move,oldMonster.protected_until,monster]);
 await db.end();
}
