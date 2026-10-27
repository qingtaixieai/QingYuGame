// Fixtures write ONLY to this isolated QA database, never the deployed game.
import pg from 'pg';
import {chromium} from 'file:///C:/Users/lenovo/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs';
import {mkdirSync,writeFileSync} from 'node:fs';import {fileURLToPath} from 'node:url';import assert from 'node:assert/strict';
const base='http://127.0.0.1:15173',out=new URL('../.local/npc-browser/',import.meta.url);mkdirSync(out,{recursive:true});
const database=process.env.NPC_QA_DATABASE||'qingyu_phase1_npc_browser';assert(/^qingyu_phase1_[a-z0-9_]+$/.test(database),'Isolated QA database required');
const db=new pg.Client({host:'127.0.0.1',port:55432,user:'worldgame',password:'local-only',database});await db.connect();
const browser=await chromium.launch({channel:'msedge',headless:true});
try{
 const ctx=await browser.newContext(),page=await ctx.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
 const post=async(path,data)=>{const r=await ctx.request.post(base+'/api'+path,{headers:{'X-World-Request':'1'},data});assert(r.ok(),await r.text());return r.json();};
 await db.query('begin');await db.query('delete from handovers');await db.query('delete from loot_containers');await db.query('delete from crimes');await db.query('delete from production_tasks');await db.query('delete from battle_encounters');await db.query('delete from guard_alarms');await db.query('delete from scene_portals');await db.query('delete from scene_documents');await db.query('delete from characters where species=$1',['guard']);await db.query('update accounts set entered=false');await db.query("update characters set life='dead',timer_end=9223372036854775807 where kind='monster'");
 const world=JSON.parse((await db.query('select document from worlds where id=1')).rows[0].document),town=world.tiles.find(t=>t.place?.type==='town'),admin=(await db.query("select id from accounts where username='admin'")).rows[0].id;
 await db.query('update accounts set q=$1,r=$2 where id=$3',[town.q,town.r,admin]);await db.query("update characters set life='alive',hp=100,max_hp=100,timer_kind=null,timer_end=0 where id=$1",[admin]);await db.query('commit');
 await page.goto(base,{waitUntil:'networkidle'});await page.getByRole('textbox',{name:'用户名',exact:true}).fill('admin');await page.getByRole('textbox',{name:'密码',exact:true}).fill('phase-one-local-test-only');await page.getByRole('button',{name:'进入青屿大陆',exact:true}).click();await page.locator('canvas').first().waitFor();await post('/scene/enter',{version:world.version});
 let guards=[];for(let i=0;i<30&&guards.length<3;i++){await page.waitForTimeout(400);guards=(await db.query('select t.actor_id,p.scene_id,p.q,p.r from npc_tasks t join scene_positions p on p.character_id=t.actor_id and p.active where t.post_id is not null')).rows;}assert.equal(guards.length,3);
 const scene=guards[0].scene_id,map=JSON.parse((await db.query('select document from scene_documents where id=$1',[scene])).rows[0].document),occupied=(await db.query('select q,r from scene_positions where scene_id=$1 and active',[scene])).rows;
 const distance=(a,b)=>Math.max(Math.abs(a.q-b.q),Math.abs(a.r-b.r),Math.abs(a.q+a.r-b.q-b.r));const cell=map.cells.filter(c=>c.walkable&&!occupied.some(p=>p.q===c.q&&p.r===c.r)).sort((a,b)=>distance(a,guards[0])-distance(b,guards[0]))[0];assert(cell);
 const target=crypto.randomUUID();await db.query('begin');await db.query("insert into characters(id,kind,species,name,q,r,bind_q,bind_r,hp,max_hp,malicious,deployed,next_move) values($1,'monster','bully','守卫验收目标',$2,$3,$2,$3,3,3,true,true,9223372036854775807)",[target,town.q,town.r]);await db.query('insert into scene_positions values($1,$2,$3,$4,true)',[target,scene,cell.q,cell.r]);await db.query("insert into inventories values($1,'wood',1)",[target]);await db.query("insert into crimes(actor,faction_id,reason,created_at) values($1,'qingyu','守卫自动流程验收',$2)",[target,Date.now()]);await db.query('commit');
 let fought=false,settled=false;const deadline=Date.now()+180000;
 while(Date.now()<deadline){const b=(await db.query('select id,turn_account_id from battle_encounters where active and scene_id=$1',[scene])).rows[0];if(b){fought=true;if(b.turn_account_id===admin)await post('/battle/end-turn',{battleId:b.id});}
  const n=Number((await db.query("select count(*) from crimes where actor=$1 and cleared_at is null",[target])).rows[0].count);if(n===0){settled=true;break;}await page.waitForTimeout(600);
 }
 assert(fought,'Guard must actually enter combat');assert(settled,'Guard must carry corpse to officer and settle, not award on kill');
 const rewards=Number((await db.query("select coalesce(sum(i.quantity),0) total from inventories i join npc_tasks t on t.actor_id=i.character_id where t.post_id is not null and i.item_code='silver_coin'")).rows[0].total);assert.equal(rewards,1);
 assert.equal(Number((await db.query('select count(*) from loot_containers where source_id=$1',[target])).rows[0].count),0);
 assert.equal(Number((await db.query('select count(*) from crimes c join npc_tasks t on t.actor_id=c.actor where t.post_id is not null')).rows[0].count),0);
 await page.reload({waitUntil:'networkidle'});await page.getByRole('button',{name:'交接与执法',exact:true}).click();await page.getByRole('button',{name:'执法与公告',exact:true}).click();
 for(const [width,height] of [[1280,800],[844,390]]){await page.setViewportSize({width,height});await page.waitForTimeout(250);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);await page.screenshot({path:fileURLToPath(new URL(`${width}.png`,out))});}
 assert.deepEqual(errors,[]);const result={guards:3,autonomousCombat:fought,corpseDelivered:settled,silver:rewards,guardCrimes:0,viewports:[1280,844],errors};writeFileSync(new URL('result.json',out),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
}finally{await browser.close();await db.end();}
