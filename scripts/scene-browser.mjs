// Only the dedicated local QA server/database. Never accepts a production URL.
import pg from 'pg';
import {chromium} from 'file:///C:/Users/lenovo/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs';
import {mkdirSync,writeFileSync} from 'node:fs';import {fileURLToPath} from 'node:url';import assert from 'node:assert/strict';
const base='http://127.0.0.1:15173',out=new URL('../.local/scene-browser/',import.meta.url);mkdirSync(out,{recursive:true});
const db=new pg.Client({host:'127.0.0.1',port:55432,user:'worldgame',password:'local-only',database:'qingyu_phase1_browser'});await db.connect();
const browser=await chromium.launch({channel:'msedge',headless:true});const errors=[],results=[];
try{
 await db.query('begin');await db.query('delete from loot_containers where battle_id is not null');await db.query('delete from battle_encounters');await db.query('delete from scene_portals');await db.query('delete from scene_documents');
 await db.query("update accounts set entered=false where username<>'admin'");await db.query("update characters set life='alive',hp=max_hp,timer_kind=null,timer_end=0,next_move=9223372036854775807");await db.query('commit');
 const page=await browser.newPage({viewport:{width:1280,height:800}});page.on('pageerror',e=>errors.push(e.message));
 page.on('response',async response=>{if(response.url().endsWith('/api/scene/move'))console.log('scene-move-response',response.status(),await response.text());});
 await page.goto(base,{waitUntil:'networkidle'});await page.getByRole('textbox',{name:'用户名',exact:true}).fill('admin');await page.getByRole('textbox',{name:'密码',exact:true}).fill('phase-one-local-test-only');await page.getByRole('button',{name:'进入青屿大陆',exact:true}).click();await page.locator('canvas').first().waitFor({state:'visible'});
 const get=async path=>{const r=await page.request.get(base+'/api'+path);assert.equal(r.ok(),true,await r.text());return r.json();};
 await page.getByRole('button',{name:'进入当前地块 · 二级地图',exact:true}).click();await page.locator('.scene-board polygon').first().waitFor();
 let state=await get('/scene');const original=state.position;assert.equal(state.map.cells.length,721);
 const next=state.map.cells.find(h=>h.walkable&&Math.max(Math.abs(h.q-original.q),Math.abs(h.r-original.r),Math.abs(h.q+h.r-original.q-original.r))===1);
 await page.getByRole('button',{name:'移动：关闭',exact:true}).click();const target=page.getByRole('button',{name:`场景格 ${next.q}, ${next.r}`,exact:true});await target.click();await page.locator('.scene-board polyline').waitFor({state:'attached'});await target.click();
 for(let i=0;i<30;i++){state=await get('/scene');if(state.position.q===next.q&&state.position.r===next.r)break;await page.waitForTimeout(150);}
 if(state.position.q!==next.q||state.position.r!==next.r){console.log('movement-diagnostic',JSON.stringify({original,next,state,detail:await page.locator('.scene-detail').allTextContents(),alerts:await page.locator('[role="status"]').allTextContents()}));await page.screenshot({path:fileURLToPath(new URL('movement-failure.png',out))});}
 assert.equal(state.position.q,next.q);assert.equal(state.position.r,next.r);
 await page.reload({waitUntil:'networkidle'});await page.locator('.scene-board polygon').first().waitFor();assert.equal(await page.getByRole('button',{name:'移动：关闭',exact:true}).isVisible(),true);assert.deepEqual((await get('/scene')).position,state.position);
 await page.getByRole('button',{name:'返回一级地图',exact:true}).click();await page.locator('canvas').first().waitFor({state:'visible'});assert.equal((await get('/scene')).active,false);
 await page.getByRole('button',{name:'进入当前地块 · 二级地图',exact:true}).click();await page.locator('.scene-board polygon').first().waitFor();assert.deepEqual((await get('/scene')).position,state.position);
 for(const [width,height] of [[1280,800],[844,390]]){await page.setViewportSize({width,height});await page.waitForTimeout(400);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);assert.equal(await page.getByRole('button',{name:'返回一级地图',exact:true}).isVisible(),true);assert.equal(await page.locator('.scene-controls button').evaluateAll(buttons=>buttons.every(b=>{const r=b.getBoundingClientRect();return b.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2));})),true,'scene controls must not be covered by sidebar or status bar');await page.screenshot({path:fileURLToPath(new URL(`${width}.png`,out))});results.push({width,height,overflow:false,sceneCells:721,controlsUncovered:true});}
 const targetActor=(await db.query("select id from accounts where username<>'admin' order by id limit 1")).rows[0].id;
 await db.query('update accounts set approved=true,entered=true,q=$1,r=$2 where id=$3',[state.map.owner.q,state.map.owner.r,targetActor]);const peer=state.map.cells.find(h=>h.walkable&&(h.q!==state.position.q||h.r!==state.position.r));
 await db.query('insert into scene_positions(character_id,scene_id,q,r,active) values($1,$2,$3,$4,true)',[targetActor,state.map.id,peer.q,peer.r]);
 const start=await page.request.post(base+'/api/battle/start',{headers:{'X-World-Request':'1'},data:{targetId:targetActor,version:state.map.worldVersion}});assert.equal(start.ok(),true,await start.text());await page.locator('.battle-board').waitFor();const combat=await get('/battle/current');assert.equal(combat.radius,15);assert.equal(combat.sceneId,state.map.id);assert.equal(combat.actors.find(a=>a.accountId===state.position.characterId).q,state.position.q);
 await page.screenshot({path:fileURLToPath(new URL('battle-844.png',out))});await db.query("update characters set life='dead',hp=0 where id=$1",[targetActor]);await db.query("delete from battle_actors where account_id=$1",[targetActor]);await page.locator('.scene-board').waitFor({timeout:15000});assert.deepEqual((await get('/scene')).position,state.position);
 assert.deepEqual(errors,[]);writeFileSync(new URL('result.json',out),JSON.stringify({results,errors,movement:true,reconnect:true,reentry:true,battlePreserved:true},null,2));console.log(JSON.stringify({results,errors,movement:true,reconnect:true,reentry:true,battlePreserved:true}));
}finally{await browser.close();await db.end();}
