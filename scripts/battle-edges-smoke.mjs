import assert from 'node:assert/strict';
import {readFileSync,writeFileSync} from 'node:fs';
import {randomBytes} from 'node:crypto';
import {WebSocket} from 'ws';
import pg from 'pg';
// Only local isolated database. Position updates arrange fixtures; actions use HTTP/WSS.
const db=new pg.Client({host:'127.0.0.1',port:55432,user:'worldgame',database:'worldgame'}),users=[],sockets=[],ids=[],checks=[];
const dirs=[[1,0],[0,1],[-1,1],[-1,0],[0,-1],[1,-1]],key=h=>`${h.q},${h.r}`;
const dist=(a,b={q:0,r:0})=>Math.max(Math.abs(a.q-b.q),Math.abs(a.r-b.r),Math.abs(a.q+a.r-b.q-b.r));
const sides=(h,R)=>[h.q,h.q+h.r,h.r,-h.q,-h.q-h.r,-h.r].flatMap((v,i)=>v===R?[i]:[]),pause=ms=>new Promise(r=>setTimeout(r,ms));
async function call(path,body,user,status=200){const res=await fetch('http://127.0.0.1:8080/api'+path,{method:body===undefined?'GET':'POST',headers:{Origin:'http://127.0.0.1:5173',Cookie:user?.cookie||'','X-World-Request':'1','Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});const data=await res.json();assert.equal(res.status,status,path+': '+JSON.stringify(data));return {data,cookie:res.headers.get('set-cookie')?.split(';')[0]||user?.cookie};}
async function current(u){return (await call('/battle/current',undefined,u)).data;}
async function connect(u){const ws=new WebSocket('ws://127.0.0.1:8080/ws',{headers:{Origin:'http://127.0.0.1:5173',Cookie:u.cookie}});sockets.push(ws);await new Promise((r,j)=>{ws.once('open',r);ws.once('error',j)});return ws;}
const passed=s=>{checks.push(s);console.log('PASS '+s);};
async function place(u,h){await db.query('update accounts set q=$1,r=$2,entered=true where id=$3',[h.q,h.r,u.data.id]);}
async function position(u,b,h){
 const occupants=(await db.query('select account_id,q,r from battle_actors where encounter_id=$1',[b])).rows;
 const displaced=occupants.find(x=>key(x)===key(h)&&x.account_id!==u.data.id);
 if(displaced){let free;for(let q=-5;q<=5&&!free;q++)for(let r=-5;r<=5&&!free;r++)if(dist({q,r})<6&&!occupants.some(x=>x.q===q&&x.r===r))free={q,r};await db.query('update battle_actors set q=$1,r=$2 where encounter_id=$3 and account_id=$4',[free.q,free.r,b,displaced.account_id]);}
 await db.query('update battle_actors set q=$1,r=$2 where account_id=$3 and encounter_id=$4',[h.q,h.r,u.data.id,b]);}
async function turn(u,b,edge=false){await db.query('update battle_encounters set turn_account_id=$1,turn_points=6,attack_used=false,turn_start_edge=$3,turn_deadline=$4 where id=$2',[u.data.id,b,edge,Date.now()+45000]);}
async function end(u,b){await call('/battle/end-turn',{battleId:b},u);}
async function withdraw(u,b,d,force=false,status=200){await call('/battle/withdraw',{battleId:b,direction:d,force},u,status);}
await db.connect();
try{
 const admin=await call('/login',JSON.parse(readFileSync(new URL('../.local/dev.json',import.meta.url),'utf8'))),world=(await call('/world',undefined,admin)).data,index=new Map(world.tiles.map(t=>[key(t),t]));
 const walk=h=>!!index.get(key(h))&&!['ocean','river','mountain'].includes(index.get(key(h)).terrain),neighbors=h=>dirs.map(([q,r])=>({q:h.q+q,r:h.r+r}));
 const existing=(await db.query('select q,r from accounts where entered=true')).rows,cell=world.tiles.find(t=>walk(t)&&neighbors(t).every(walk)&&neighbors(neighbors(t)[0]).every(walk)&&existing.every(h=>dist(h,t)>3));assert(cell);
 const adjacent=neighbors(cell)[0],far=neighbors(adjacent)[0],suffix=Date.now().toString(36),password=randomBytes(16).toString('base64url');
 for(let i=0;i<6;i++){const credentials={username:`edge_${suffix}_${i}`,password};await call('/register',credentials);const u=await call('/login',credentials);users.push(u);await call(`/admin/accounts/${u.data.id}/approval`,{approved:true},admin);await place(u,i<3?cell:i===3?adjacent:far);await connect(u);}
 const [a,b,c,d,e,f]=users;sockets[1].close();await pause(450);
 const queued=(await call('/move',{q:cell.q-1,r:cell.r,version:world.version},e)).data.path;assert(queued.some(h=>key(h)===key(cell)));
 const bid=(await call('/battle/start',{targetId:b.data.id,version:world.version},a)).data.id;ids.push(bid);
 let state=await current(a);assert.equal(state.actors.length,3);assert(state.actors.every(x=>dist(x)<state.radius));assert.equal(new Set(state.actors.map(key)).size,3);assert.equal(state.actors.find(x=>x.accountId===b.data.id).online,false);assert.equal(state.edges.length,6);passed('All same-cell actors including offline spawn uniquely in interior');
 await pause(900);const stopped=(await call('/players',undefined,e)).data.players.find(p=>p.id===e.data.id);assert.equal(stopped.moving,false);assert.notEqual(key(stopped),key(cell));assert.notEqual(key(stopped),key({q:cell.q-1,r:cell.r}));passed('Previously queued world route stops before newly sealed battle cell');
 await call('/move',{q:cell.q,r:cell.r,version:world.version},d,400);await call('/move',{q:cell.q,r:cell.r,version:world.version},a,400);await call('/collect',{q:cell.q,r:cell.r,version:world.version},a,400);
 const route=(await call('/move',{q:cell.q-1,r:cell.r,version:world.version},d)).data.path;assert(route.every(h=>key(h)!==key(cell)));await call('/stop',{},d);await place(d,adjacent);passed('World combat cell blocked, routes go around, combatants cannot act in world');
 await call('/battle/join',{battleId:bid,version:world.version},d);state=await current(d);let reinforcement=state.actors.find(x=>x.accountId===d.data.id);assert(sides(reinforcement,state.radius).includes(0));assert.equal(reinforcement.entryRound,state.round+1);await call('/battle/end-turn',{battleId:bid},d,400);passed('Adjacent reinforcement uses incoming outer side and waits next round');
 await connect(b);await db.query('update battle_actors set entry_round=1 where encounter_id=$1',[bid]);
 await position(a,bid,{q:5,r:-3});await position(b,bid,{q:0,r:0});await position(c,bid,{q:-3,r:0});await position(d,bid,{q:-4,r:0});await turn(a,bid);
 await call('/battle/step',{battleId:bid,q:6,r:-3},a);assert.equal((await current(a)).active,true);await withdraw(a,bid,0,true,400);await withdraw(a,bid,3,false,400);passed('Outer step does not auto-exit or grant same-turn forced withdrawal');
 await withdraw(a,bid,0);state=await current(a);assert.equal(state.actors.find(x=>x.accountId===a.data.id).withdrawDirection,0);await call('/battle/step',{battleId:bid,q:5,r:-3},a,400);
 for(let i=0;i<8&&(await current(a)).active;i++){state=await current(a);await end(users.find(u=>u.data.id===state.turnAccountId),bid);}assert.equal((await current(a)).active,false);assert.equal(key((await call('/me',undefined,a)).data),key(adjacent));passed('Normal withdrawal ends turn and exits on next turn');
 await place(a,adjacent);await call('/battle/join',{battleId:bid,version:world.version},a);await db.query('update battle_actors set entry_round=1 where encounter_id=$1',[bid]);
 await position(a,bid,{q:5,r:-3});await position(b,bid,{q:6,r:-3});await turn(a,bid);await call('/battle/attack',{battleId:bid,targetId:b.data.id},a);await turn(b,bid,true);await withdraw(b,bid,0);state=await current(b);assert.equal(state.actors.find(x=>x.accountId===b.data.id).withdrawDirection,0);
 await db.query('update battle_actors set initiative=initiative+100 where encounter_id=$1',[bid]);for(let i=0;i<4;i++)await db.query('update battle_actors set initiative=$1 where encounter_id=$2 and account_id=$3',[i,bid,[c,a,b,d][i].data.id]);
 await turn(c,bid);await end(c,bid);state=await current(b);assert.equal(state.actors.find(x=>x.accountId===b.data.id).withdrawDirection,null);assert(state.events.some(x=>x.kind==='withdraw-interrupted'));assert.equal(state.turnAccountId,a.data.id);passed('Actual hit interrupts pending withdrawal; telegraph alone does not');
 await call('/battle/attack',{battleId:bid,targetId:b.data.id},a);await turn(b,bid,true);await call('/battle/step',{battleId:bid,q:6,r:-2},b);state=await current(a);assert(!state.intents.some(x=>x.attackerId===a.data.id));const count=async()=>Number((await db.query("select count(*) from battle_events where encounter_id=$1 and kind='attack' and actor_id=$2",[bid,a.data.id])).rows[0].count);const before=await count();await turn(c,bid);await end(c,bid);assert.equal(await count(),before);passed('Opportunity attack consumes telegraph without duplicate hit');
 await place(e,adjacent);await place(f,adjacent);const next=(await call('/battle/start',{targetId:f.data.id,version:world.version},e)).data.id;ids.push(next);
 await position(a,bid,{q:6,r:-3});await turn(a,bid,true);assert.equal((await current(a)).edges[0].battleId,next);await withdraw(a,bid,0,true);state=await current(a);assert.equal(state.id,next);reinforcement=state.actors.find(x=>x.accountId===a.data.id);assert(sides(reinforcement,state.radius).includes(3));assert.equal(reinforcement.entryRound,state.round+1);passed('Forced withdrawal transfers to neighbor battle opposite edge next round');
 const border=world.tiles.find(t=>walk(t)&&neighbors(t).some(h=>!walk(h))&&existing.every(h=>dist(h,t)>3));await db.query('update battle_encounters set world_q=$1,world_r=$2 where id=$3',[border.q,border.r,next]);state=await current(e);const closed=state.edges.find(x=>!x.walkable),corner=[{q:6,r:0},{q:0,r:6},{q:-6,r:6},{q:-6,r:0},{q:0,r:-6},{q:6,r:-6}].find(h=>sides(h,6).includes(closed.direction));assert.equal(sides(corner,6).length,2);await position(e,next,corner);await turn(e,next,true);await withdraw(e,next,closed.direction,true,400);passed('Corners have two sides; impassable world direction rejects withdrawal');
 await db.query('delete from battle_intents where encounter_id=$1',[bid]);await db.query('delete from battle_actors where encounter_id=$1 and account_id=$2',[bid,d.data.id]);await position(b,bid,{q:6,r:-3});await turn(b,bid,true);await withdraw(b,bid,0,true);assert.equal((await current(c)).active,false);assert.equal(key((await call('/me',undefined,c)).data),key(cell));passed('Last actor stays at original world cell when encounter closes');
 writeFileSync(new URL('../.local/battle-edges-report.json',import.meta.url),JSON.stringify({date:new Date().toISOString(),checks},null,2));
}finally{for(const s of sockets)s.close();await pause(150);for(const id of ids)await db.query('delete from battle_encounters where id=$1',[id]);for(const u of users){await db.query('delete from admin_audit where target=$1',[u.data.id]);await db.query('delete from accounts where id=$1',[u.data.id]);}await db.end();}
