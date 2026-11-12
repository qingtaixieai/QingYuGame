// Local disposable QA only. No production URL or server credentials are accepted.
import assert from 'node:assert/strict';
import {mkdirSync,writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import pg from 'pg';
const {chromium}=await import(process.env.QINGYU_PLAYWRIGHT_MODULE||'playwright');
const base='http://127.0.0.1:15173';
const db=new pg.Client({host:'127.0.0.1',port:55432,user:'worldgame',password:'local-only',database:'qingyu_phase1_logistics_browser'});
const out=new URL('../.local/engineering-browser/',import.meta.url);mkdirSync(out,{recursive:true});
const username='eng_'+crypto.randomUUID().slice(0,8),password='engineering-local-test';
const browser=await chromium.launch({channel:process.env.QINGYU_BROWSER_CHANNEL||'msedge',headless:true});
await db.connect();
const context=await browser.newContext({viewport:{width:1280,height:800}}),page=await context.newPage();
page.setDefaultTimeout(60000);
const errors=[],sockets=new Set(),checks=[];
let fixtureBattle=null;
page.on('pageerror',error=>errors.push(error.message));
page.on('websocket',socket=>{if(new URL(socket.url()).pathname!=='/ws')return;sockets.add(socket);socket.on('close',()=>sockets.delete(socket));});
async function login(name,pass){await page.getByRole('textbox',{name:'用户名',exact:true}).fill(name);await page.getByRole('textbox',{name:'密码',exact:true}).fill(pass);await page.getByRole('button',{name:'进入青屿大陆',exact:true}).click();}
async function until(check){for(let i=0;i<50;i++){if(await check())return;await page.waitForTimeout(100);}assert.fail('state did not settle');}
try {
  await page.goto(base,{waitUntil:'domcontentloaded'});
  await page.getByRole('button',{name:'申请加入',exact:true}).click();
  await page.getByRole('textbox',{name:'用户名',exact:true}).fill(username);
  await page.getByRole('textbox',{name:'密码',exact:true}).fill(password);
  await page.getByRole('button',{name:'提交加入申请',exact:true}).click();
  await page.getByText('申请已提交。请登录查看审批状态。',{exact:true}).waitFor();
  await login(username,password);
  await page.getByRole('button',{name:'查看审批状态',exact:true}).waitFor();
  assert.equal(sockets.size,0,'pending account must not connect world');
  assert.equal((await context.request.get(base+'/api/world')).status(),403);
  await page.getByRole('button',{name:'退出账号',exact:true}).click();
  await login('admin','phase-one-local-test-only');
  await page.getByRole('button',{name:'管理后台',exact:true}).waitFor();await until(()=>sockets.size===1);
  for(const [name,width,height] of [['desktop',1280,800],['mobile',844,390]]) {
    await page.setViewportSize({width,height});
    await page.getByRole('button',{name:'管理后台',exact:true}).click();
    await page.getByRole('button',{name:'关闭管理后台',exact:true}).waitFor();
    await page.getByText(username,{exact:true}).first().waitFor();
    assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
    await page.screenshot({path:fileURLToPath(new URL(name+'-admin.png',out)),fullPage:true});
    await page.getByRole('button',{name:'关闭管理后台',exact:true}).click();
    assert.equal(sockets.size,1,'admin opening must not duplicate world connection');
    await page.screenshot({path:fileURLToPath(new URL(name+'-world.png',out)),fullPage:true});
    checks.push({name,width,height,adminVisible:true,worldSocketCount:sockets.size});
  }
  const get=async path=>{const r=await context.request.get(base+'/api'+path);assert(r.ok(),await r.text());return r.json();};
  const post=async(path,data)=>{const r=await context.request.post(base+'/api'+path,{headers:{'X-World-Request':'1'},data});assert(r.ok(),await r.text());return r.json();};
  const world=await get('/world');let scene=await get('/scene');
  if(!scene.active){await post('/scene/enter',{version:world.version});scene=await get('/scene');}
  assert.equal((await get('/battle/current')).active,false,'QA account already in battle; do not overwrite');
  const target=(await db.query('select id from accounts where username=$1',[username])).rows[0].id;
  const positions=(await db.query('select q,r from scene_positions where active and scene_id=$1',[scene.map.id])).rows;
  const cell=scene.map.cells.find(c=>c.walkable&&!positions.some(p=>p.q===c.q&&p.r===c.r));assert(cell);
  await db.query('update accounts set approved=true,entered=true,q=$1,r=$2 where id=$3',[scene.map.owner.q,scene.map.owner.r,target]);
  await db.query('update characters set q=$1,r=$2 where id=$3',[scene.map.owner.q,scene.map.owner.r,target]);
  await db.query('insert into scene_positions(character_id,scene_id,q,r,active) values($1,$2,$3,$4,true)',[target,scene.map.id,cell.q,cell.r]);
  fixtureBattle=(await post('/battle/start',{targetId:target,version:world.version})).id;
  await page.locator('.battle-board').waitFor();
  for(const [name,width,height] of [['desktop',1280,800],['mobile',844,390]]){
    await page.setViewportSize({width,height});
    assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
    await page.screenshot({path:fileURLToPath(new URL(name+'-battle.png',out)),fullPage:true});
  }
  await db.query('delete from battle_encounters where id=$1',[fixtureBattle]);fixtureBattle=null;
  // Track the new document only; Playwright may omit close events on navigation.
  sockets.clear();
  await page.reload({waitUntil:'domcontentloaded'});await page.getByRole('button',{name:'管理后台',exact:true}).waitFor();
  await until(()=>sockets.size===1);
  await page.setViewportSize({width:1280,height:800});
  const expand=page.getByRole('button',{name:'展开顶部横栏',exact:true});
  if(await expand.isVisible())await expand.click();
  await page.getByRole('button',{name:'退出登录',exact:true}).first().click();
  await page.getByRole('button',{name:'进入青屿大陆',exact:true}).waitFor();
  await until(()=>sockets.size===0);
  assert.deepEqual(errors,[]);
  const result={pendingGate:true,adminGetAndPostAlreadyCoveredByJUnit:true,checks,battleViewports:[1280,844],logoutClosesSocket:true,errors};
  writeFileSync(new URL('result.json',out),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
} catch(error) {
  await page.screenshot({path:fileURLToPath(new URL('failure.png',out)),fullPage:true});
  console.error((await page.locator('body').innerText()).slice(0,2500));
  throw error;
} finally {
  await browser.close();
  if(fixtureBattle)await db.query('delete from battle_encounters where id=$1',[fixtureBattle]);
  await db.query('delete from accounts where username=$1',[username]);
  await db.end();
}
