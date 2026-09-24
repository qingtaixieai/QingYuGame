import assert from 'node:assert/strict';
import {readFileSync,writeFileSync,existsSync} from 'node:fs';
import {randomBytes} from 'node:crypto';
import {WebSocket} from 'ws';
import pg from 'pg';
const db=new pg.Client({host:'127.0.0.1',port:55432,user:'worldgame',database:'worldgame'}),users=[],sockets=[],checks=[];
const pause=ms=>new Promise(r=>setTimeout(r,ms)),key=h=>`${h.q},${h.r}`;
const passed=s=>{checks.push(s);console.log('PASS '+s)};
async function call(path,body,u,status=200){const r=await fetch('http://127.0.0.1:8080/api'+path,{method:body===undefined?'GET':'POST',headers:{Origin:'http://127.0.0.1:5173',Cookie:u?.cookie||'','X-World-Request':'1','Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});const data=await r.json();assert.equal(r.status,status,path+': '+JSON.stringify(data));return {data,cookie:r.headers.get('set-cookie')?.split(';')[0]||u?.cookie};}
async function connect(u){const ws=new WebSocket('ws://127.0.0.1:8080/ws',{headers:{Origin:'http://127.0.0.1:5173',Cookie:u.cookie}});sockets.push(ws);await new Promise((r,j)=>{ws.once('message',r);ws.once('error',j)});return ws;}
let battleId,originalFerry,originalPassengers,passengerPositions,ferryMutated=false;
await db.connect();
try{
 const admin=await call('/login',JSON.parse(readFileSync(new URL('../.local/dev.json',import.meta.url),'utf8'))),world=(await call('/world',undefined,admin)).data,version=world.version;
 if(existsSync(new URL('../.local/expansion-before.json',import.meta.url))){
 const before=JSON.parse(readFileSync(new URL('../.local/expansion-before.json',import.meta.url),'utf8')),index=new Map(world.tiles.map(t=>[key(t),t]));
 for(const t of before.world.tiles){assert.equal(index.get(key(t)).terrain,t.terrain);assert.equal(index.get(key(t)).road,t.road);if(t.place)assert.deepEqual(index.get(key(t)).place,t.place)}
 const oldPositions=(await db.query('select id,q,r from accounts')).rows;for(const a of before.accounts)assert.equal(key(oldPositions.find(p=>p.id===a.id)),key(a));passed('Persisted mainland terrain, landmarks and existing player positions preserved on migration');
 }
 const suffix=Date.now().toString(36),password=randomBytes(16).toString('base64url');
 for(let i=0;i<3;i++){const credentials={username:`sail_${suffix}_${i}`,password};await call('/register',credentials);const u=await call('/login',credentials);users.push(u);await call(`/admin/accounts/${u.data.id}/approval`,{approved:true},admin);await connect(u);}
 const [a,b,c]=users;const existing=(await db.query('select q,r from accounts where entered=true')).rows;const cell=world.tiles.find(t=>t.terrain==='plain'&&!existing.some(p=>key(p)===key(t)));
 for(const u of users)await db.query('update accounts set q=$1,r=$2 where id=$3',[cell.q,cell.r,u.data.id]);
 battleId=(await call('/battle/start',{targetId:b.data.id,version},a)).data.id;
 const current=async()=> (await call('/battle/current',undefined,a)).data;
 const count=async(kind='attack')=>Number((await db.query('select count(*) from battle_events where encounter_id=$1 and kind=$2',[battleId,kind])).rows[0].count);
 async function fixture(positions){await db.query('delete from battle_intents where encounter_id=$1',[battleId]);await db.query('update battle_actors set q=q+100,r=r+100,initiative=initiative+100 where encounter_id=$1',[battleId]);for(let i=0;i<3;i++)await db.query('update battle_actors set q=$1,r=$2,initiative=$3,entry_round=1,withdraw_direction=null where encounter_id=$4 and account_id=$5',[...positions[i],i,battleId,users[i].data.id]);await turn(a);}
 async function turn(u,points=6){await db.query('update battle_encounters set turn_account_id=$1,turn_points=$2,attack_used=false,turn_deadline=$3 where id=$4',[u.data.id,points,Date.now()+45000,battleId]);}
 const mark=(u,q,r)=>call('/battle/attack',{battleId,q,r},u),move=(u,q,r,status=200)=>call('/battle/step',{battleId,q,r},u,status),end=u=>call('/battle/end-turn',{battleId},u);
 await fixture([[0,0],[2,0],[-4,0]]);let n=await count();await mark(a,1,0);assert.equal((await current()).intents.length,1);await turn(b);await move(b,1,0);assert.equal(await count(),n);assert.equal((await current()).intents.length,1);await move(b,2,0);assert.equal(await count(),n+1);assert.equal((await current()).intents.length,0);passed('Empty-cell marking: entering harmless, leaving hits current occupant exactly once');
 await fixture([[0,0],[2,0],[-4,0]]);n=await count();await mark(a,1,0);await turn(b);await move(b,1,-1);let state=await current();assert.equal(state.turnPoints,4);assert.equal(await count(),n+1);assert.equal(state.intents.length,0);passed('Multi-cell route processes both entry and exit and deducts actual path length');
 await fixture([[0,0],[1,0],[-4,0]]);n=await count();await mark(a,1,0);await move(a,0,1);assert.equal(await count(),n);assert.equal((await current()).intents.length,1);await move(a,-1,1);assert.equal(await count(),n+1);assert.equal((await current()).intents.length,0);passed('Attacker may remain adjacent; first step outside range resolves stored attack once');
 await fixture([[0,0],[3,0],[-4,0]]);let misses=await count('miss');await mark(a,1,0);await move(a,-1,0);assert.equal(await count('miss'),misses+1);assert.equal((await current()).intents.length,0);passed('Attacker leaving empty marked cell range misses and clears it');
 await fixture([[0,0],[2,0],[-4,0]]);n=await count();await mark(a,1,0);await turn(b);await move(b,1,0);await turn(c);await end(c);state=await current();assert.equal(state.turnAccountId,a.data.id);assert.equal(await count(),n+1);assert.equal(state.intents.length,0);passed('Next attacker turn hits whoever occupies the marked cell, including a later entrant');
 await fixture([[0,0],[3,0],[-4,0]]);misses=await count('miss');await mark(a,1,0);await turn(c);await end(c);assert.equal(await count('miss'),misses+1);passed('Empty cell expires as a miss on next attacker turn');
 await fixture([[0,0],[1,0],[-4,0]]);await move(a,3,0);state=await current();assert.equal(state.turnPoints,2);assert.equal(key(state.actors.find(x=>x.accountId===a.data.id)),'3,0');await move(a,6,0,400);await move(a,1,0,400);await move(a,7,0,400);assert.equal((await current()).turnPoints,2);passed('Shortest path detours occupied cells; over-budget, occupied and out-of-bounds moves are atomic rejections');
 await db.query('delete from battle_encounters where id=$1',[battleId]);battleId=null;
 originalFerry=(await db.query('select * from ferry_state')).rows[0];originalPassengers=(await db.query('select * from ferry_passengers')).rows;passengerPositions=(await db.query('select id,q,r from accounts where id in (select account_id from ferry_passengers)')).rows;
 assert.equal(originalPassengers.length,0,'Run local ferry test while no real passengers are aboard');
 ferryMutated=true;await db.query("update ferry_state set wood=0,stone=0,built=false,phase='mainland',route_index=0,next_at=0 where id=1");
 let ferry=(await call('/players',undefined,a)).data.ferry;
 for(const u of users)await db.query('insert into inventories values($1,\'wood\',20),($1,\'stone\',20) on conflict(account_id,item_code) do update set quantity=20',[u.data.id]);
 await call('/ferry/contribute',{version,item:'wood',quantity:1},a,400);
 for(const u of users)await db.query('update accounts set q=$1,r=$2 where id=$3',[ferry.mainlandPort.q,ferry.mainlandPort.r,u.data.id]);
 await call('/ferry/contribute',{version,item:'wood',quantity:0},a,400);
 await Promise.all([call('/ferry/contribute',{version,item:'wood',quantity:4},a),call('/ferry/contribute',{version,item:'wood',quantity:4},b)]);
 await call('/ferry/contribute',{version,item:'stone',quantity:4},c);ferry=(await call('/players',undefined,a)).data.ferry;assert(ferry.built);assert.equal(ferry.wood,8);assert.equal(ferry.stone,4);await call('/ferry/contribute',{version,item:'stone',quantity:1},c,400);passed('Shared project consumes wood and stone atomically; invalid and post-completion contributions rejected');
 await call('/ferry/board',{version},a);await call('/ferry/board',{version},b);await call('/ferry/board',{version},c);ferry=(await call('/players',undefined,a)).data.ferry;assert.equal(ferry.passengerIds.length,3);await call('/ferry/board',{version},a,400);
 await call('/move',{version,...ferry.mainlandPort},a,400);await call('/collect',{version,...ferry.mainlandPort},a,400);await call('/battle/start',{version,targetId:b.data.id},a,400);await call('/emote',{version,code:'happy'},a,400);passed('Multiple passengers board; duplicate boarding and world actions while aboard rejected');
 sockets[1].close();await pause(450);
 async function phase(name){for(let deadline=Date.now()+2*(ferry.travelMs+ferry.dwellMs)+5000;Date.now()<deadline;){const f=(await call('/players',undefined,a)).data.ferry;if(f.phase===name)return f;await pause(100)}throw Error('Did not reach phase '+name)}
 ferry=await phase('outbound');await call('/ferry/disembark',{version},a,400);ferry=await phase('island');assert(ferry.passengerIds.includes(b.data.id));assert.equal(key((await call('/me',undefined,b)).data),key(ferry));
 await call('/ferry/disembark',{version},a);assert.equal(key((await call('/me',undefined,a)).data),key(ferry.islandLanding));await connect(b);await call('/ferry/disembark',{version},b);passed('Offline passenger follows boat, reconnects and disembarks; midsea exit rejected');
 ferry=await phase('mainland');await call('/ferry/disembark',{version},c);assert.equal(key((await call('/me',undefined,c)).data),key(ferry.mainlandPort));ferry=await phase('outbound');assert.equal(ferry.passengerIds.length,0);await phase('island');await call('/ferry/board',{version},a);await phase('mainland');await call('/ferry/disembark',{version},a);passed('Passengers can remain aboard for return; empty ferry keeps cycling and picks up island travelers');
 writeFileSync(new URL('../.local/expedition-report.json',import.meta.url),JSON.stringify({date:new Date().toISOString(),checks},null,2));
}finally{
 for(const ws of sockets)ws.close();await pause(200);if(battleId)await db.query('delete from battle_encounters where id=$1',[battleId]);
 if(ferryMutated){await db.query('delete from ferry_passengers');await db.query('update ferry_state set wood=$1,stone=$2,built=$3,phase=$4,route_index=$5,next_at=$6,saved_at=$7 where id=1',[originalFerry.wood,originalFerry.stone,originalFerry.built,originalFerry.phase,originalFerry.route_index,originalFerry.next_at,Date.now()]);for(const p of originalPassengers)await db.query('insert into ferry_passengers values($1)',[p.account_id]);for(const p of passengerPositions)await db.query('update accounts set q=$1,r=$2 where id=$3',[p.q,p.r,p.id]);}
 for(const u of users){await db.query('delete from admin_audit where target=$1',[u.data.id]);await db.query('delete from accounts where id=$1',[u.data.id]);}await db.end();
}
