import assert from 'node:assert/strict';
import {readFileSync,writeFileSync} from 'node:fs';
import pg from 'pg';import {WebSocket} from 'ws';
const origin='http://127.0.0.1:5173',pause=ms=>new Promise(r=>setTimeout(r,ms));
const db=new pg.Client({host:'127.0.0.1',port:55432,user:'worldgame',database:'worldgame'});await db.connect();
const ids=[],sockets=[],checks=[];
async function call(path,body,cookie='',status=200){const r=await fetch('http://127.0.0.1:8080/api'+path,{method:body===undefined?'GET':'POST',headers:{Origin:origin,'X-World-Request':'1','Content-Type':'application/json',Cookie:cookie},body:body===undefined?undefined:JSON.stringify(body)});const data=await r.json();assert.equal(r.status,status,path+JSON.stringify(data));return {data,cookie:r.headers.get('set-cookie')?.split(';')[0]||cookie};}
const passed=s=>{checks.push(s);console.log('PASS '+s);};
async function wait(fn){for(let i=0;i<100;i++){if(await fn())return;await pause(80);}throw Error('Timed out');}
async function connect(c){const ws=new WebSocket('ws://127.0.0.1:8080/ws',{headers:{Cookie:c,Origin:origin}});const state={ws,last:null};sockets.push(ws);ws.on('message',m=>state.last=JSON.parse(m));await new Promise((r,j)=>{ws.once('open',r);ws.once('error',j);});return state;}
try{
const admin=await call('/login',JSON.parse(readFileSync('../.local/dev.json','utf8')));
const users=[];for(let i=0;i<2;i++){const credentials={username:'resource_'+Date.now().toString(36)+'_'+i,password:'local-resource-test-only'};await call('/register',credentials);const u=await call('/login',credentials);ids.push(u.data.id);await call('/inventory',undefined,u.cookie,403);await call(`/admin/accounts/${u.data.id}/approval`,{approved:true},admin.cookie);users.push(u);}
const [a,b]=users;await connect(a.cookie);const observer=await connect(b.cookie);
const world=(await call('/world',undefined,admin.cookie)).data,state=()=>call('/players',undefined,admin.cookie).then(x=>x.data);
const bag=async u=>(await call('/inventory',undefined,u.cookie)).data,count=async(u,code)=>(await bag(u)).find(i=>i.code===code).quantity;
const collect=(u,n,status=200)=>call('/collect',{q:n.q,r:n.r,version:world.version},u.cookie,status);
const teleport=async(u,n)=>{await call('/stop',{},u.cookie);await db.query('update accounts set q=$1,r=$2 where id=$3',[n.q,n.r,u.data.id]);};
const adjacent=(t,n)=>(Math.abs(t.q-n.q)+Math.abs(t.r-n.r)+Math.abs(t.q+t.r-n.q-n.r))/2===1&&!['mountain','ocean','river'].includes(t.terrain);
const nodes=(await state()).resources,stone=nodes.find(n=>n.kind==='stone'),tree=nodes.find(n=>n.kind==='wood'&&world.tiles.some(t=>adjacent(t,n)));
assert(nodes.filter(n=>n.kind==='stone').every(n=>world.tiles.some(t=>t.q===n.q&&t.r===n.r&&t.terrain==='plain')));passed('valid resource terrain');
await collect(a,stone,400);await call(`/admin/accounts/${a.data.id}/inventory`,undefined,b.cookie,403);passed('remote collection and unauthorized inventory denied');
await teleport(a,stone);await teleport(b,stone);const race=await Promise.allSettled([collect(a,stone),collect(b,stone)]);assert.equal(race.filter(x=>x.status==='fulfilled').length,1);assert.equal(await count(a,'stone')+await count(b,'stone'),1);passed('concurrent pickup awards once');
await wait(async()=>{const n=(await state()).resources.find(n=>n.id===stone.id);return n.readyAt===0&&(n.q!==stone.q||n.r!==stone.r);});passed('stone respawns on another free plain');
await teleport(a,tree);await teleport(b,tree);await collect(a,tree);await collect(b,tree,400);await collect(a,tree,400);await wait(()=>observer.last?.actions.some(x=>x.accountId===a.data.id));passed('exclusive forest lock and shared action progress');
const neighbor=world.tiles.find(t=>adjacent(t,tree));await call('/move',{q:neighbor.q,r:neighbor.r,version:world.version},a.cookie);assert(!(await state()).actions.some(x=>x.accountId===a.data.id));await pause(1600);assert.equal(await count(a,'wood'),0);passed('movement cancels without reward');
await collect(b,tree);observer.ws.close();await wait(async()=>await count(b,'wood')===1);assert((await state()).resources.find(n=>n.id===tree.id).readyAt>Date.now());await collect(b,tree,400);passed('offline completion awards and persists stump timer');
assert.equal((await call(`/admin/accounts/${b.data.id}/inventory`,undefined,admin.cookie)).data.find(i=>i.code==='wood').quantity,1);passed('admin sees offline inventory');
await wait(async()=>(await state()).resources.find(n=>n.id===tree.id).readyAt===0);passed('tree regrows in place');
await collect(b,tree);await call(`/admin/accounts/${b.data.id}/approval`,{approved:false},admin.cookie);assert(!(await state()).actions.some(x=>x.accountId===b.data.id));await call(`/admin/accounts/${b.data.id}/approval`,{approved:true},admin.cookie);passed('revocation releases reservation');
await teleport(a,tree);await collect(a,tree);const before=await bag(a),beforeB=await bag(b);await call('/admin/regenerate',{confirmation:'重新生成世界'},admin.cookie);assert.deepEqual(await bag(a),before);assert.deepEqual(await bag(b),beforeB);assert.equal((await state()).actions.length,0);await collect(a,tree,400);passed('world reset preserves inventory and cancels old actions');
writeFileSync('../.local/resource-report.json',JSON.stringify({date:new Date().toISOString(),passed:checks.length,checks},null,2));
}finally{for(const s of sockets)s.close();for(const id of ids){await db.query('delete from admin_audit where target=$1',[id]);await db.query('delete from accounts where id=$1',[id]);}await db.end();}
