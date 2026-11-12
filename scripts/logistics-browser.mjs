// Local isolated QA only; never points at the deployed server.
import pg from 'pg';
import assert from 'node:assert/strict';
import {mkdirSync,writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {chromium} from 'file:///C:/Users/lenovo/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs';
const base='http://127.0.0.1:15173';
const db=new pg.Client({host:'127.0.0.1',port:55432,user:'worldgame',password:'local-only',database:'qingyu_phase1_logistics_browser'});
const out=new URL('../.local/logistics-browser/',import.meta.url);mkdirSync(out,{recursive:true});
await db.connect();const browser=await chromium.launch({channel:'msedge',headless:true});
try{
 const world=JSON.parse((await db.query('select document from worlds where id=1')).rows[0].document);const town=world.tiles.find(t=>t.place?.type==='town');assert(town);
 await db.query("update accounts set q=$1,r=$2 where username='admin'",[town.q,town.r]);
 const ctx=await browser.newContext(),page=await ctx.newPage(),errors=[];page.setDefaultTimeout(90000);page.on('pageerror',e=>errors.push(e.message));
 await page.goto(base,{waitUntil:'domcontentloaded',timeout:120000});await page.getByRole('textbox',{name:'用户名',exact:true}).fill('admin');await page.getByRole('textbox',{name:'密码',exact:true}).fill('phase-one-local-test-only');await page.getByRole('button',{name:'进入青屿大陆',exact:true}).click();await page.locator('canvas').first().waitFor();
 const active=(await db.query("select exists(select 1 from scene_positions p join accounts a on a.id=p.character_id where a.username='admin' and p.active) as active")).rows[0].active;
 if(!active){const entered=await ctx.request.post(base+'/api/scene/enter',{headers:{'X-World-Request':'1'},data:{version:world.version}});assert(entered.ok(),await entered.text());}
 const response=await ctx.request.get(base+'/api/logistics');assert(response.ok());const data=await response.json();assert.equal(data.admin,true);assert(Array.isArray(data.needs));assert(Array.isArray(data.events));
 const checks=[];
 for(const [name,width,height] of [['desktop',1280,800],['mobile',844,390]]){
  await page.setViewportSize({width,height});await page.getByRole('button',{name:/交接与执法/}).click();const dialog=page.getByRole('dialog',{name:'交接与执法'});await dialog.waitFor();
  const details=dialog.locator('details').filter({has:page.locator('summary',{hasText:'官方后勤状态'})});await details.locator('summary').click();await details.scrollIntoViewIfNeeded();await details.getByText('仅供查看，暂不接受玩家配送订单。').waitFor();
  const box=await dialog.boundingBox();assert(box&&box.width<=width&&box.x>=0&&box.x+box.width<=width+1);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
  await page.screenshot({path:fileURLToPath(new URL(name+'.png',out)),fullPage:true});checks.push({name,width,height,dialog:box});await dialog.getByRole('button',{name:'关闭',exact:true}).click();
 }
 assert.deepEqual(errors,[]);writeFileSync(new URL('result.json',out),JSON.stringify({checks,errors,adminReadOnly:true},null,2));console.log(JSON.stringify({passed:checks.length,errors}));
}finally{await browser.close();await db.end();}
