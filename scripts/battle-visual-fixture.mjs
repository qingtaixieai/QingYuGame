import pg from 'pg';import {randomBytes} from 'node:crypto';import {writeFileSync} from 'node:fs';
const db=new pg.Client({host:'127.0.0.1',port:55432,user:'worldgame',database:'worldgame'});await db.connect();
const origin='http://127.0.0.1:5173',users=[],password=randomBytes(18).toString('base64url');
async function call(p,b,c=''){const r=await fetch('http://127.0.0.1:8080/api'+p,{method:b?'POST':'GET',headers:{Origin:origin,'X-World-Request':'1','Content-Type':'application/json',Cookie:c},body:b?JSON.stringify(b):undefined});if(!r.ok)throw Error(await r.text());return {data:await r.json(),cookie:r.headers.get('set-cookie')?.split(';')[0]||c};}
const world=JSON.parse((await db.query('select document from worlds where id=1')).rows[0].document),occupied=new Set((await db.query('select q,r from accounts where entered=true')).rows.map(p=>p.q+','+p.r)),cell=world.tiles.find(t=>t.terrain==='plain'&&!occupied.has(t.q+','+t.r));
for(let i=0;i<3;i++){const credentials={username:'visual_'+Date.now().toString(36)+'_'+i,password};await call('/register',credentials);const u=await call('/login',credentials);await db.query('update accounts set approved=true,entered=true,q=$1,r=$2 where id=$3',[cell.q,cell.r,u.data.id]);users.push({...u,credentials});}
const battle=(await call('/battle/start',{targetId:users[1].data.id,version:world.version},users[0].cookie)).data.id;
await db.query('update battle_encounters set turn_deadline=$1 where id=$2',[Date.now()+3600000,battle]);
writeFileSync(new URL('../.local/battle-visual.json',import.meta.url),JSON.stringify({users,battle,cell}));console.log('Local visual fixture ready');await db.end();
