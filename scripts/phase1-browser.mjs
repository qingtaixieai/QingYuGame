// Uses only the dedicated phase-one QA server and an isolated browser profile.
import {chromium} from 'file:///C:/Users/lenovo/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs';
import {mkdirSync,writeFileSync} from 'node:fs';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
const base='http://127.0.0.1:15173';
const out=new URL('../.local/phase1-browser/',import.meta.url);mkdirSync(out,{recursive:true});
const browser=await chromium.launch({channel:'msedge',headless:true});
const errors=[],results=[];
let closedEdges=0;
try{
 const page=await browser.newPage();
 page.on('pageerror',e=>errors.push(e.message));
 page.on('websocket',socket=>socket.on('framereceived',frame=>{try{const state=JSON.parse(String(frame.payload));if(state.type==='state')closedEdges=state.blockedEdges?.length||0;}catch{}}));
 await page.goto(base,{waitUntil:'networkidle'});
 await page.getByRole('textbox',{name:'用户名',exact:true}).fill('admin');
 await page.getByRole('textbox',{name:'密码',exact:true}).fill('phase-one-local-test-only');
 await page.getByRole('button',{name:'进入青屿大陆',exact:true}).click();
 await page.locator('canvas').first().waitFor({state:'visible'});
 for(const [width,height] of [[1280,800],[844,390]]){
   await page.setViewportSize({width,height});await page.waitForTimeout(800);
   const locate=page.getByRole('button',{name:'定位自己',exact:true});
   if(!await locate.isVisible())await page.getByRole('button',{name:'展开地图工具',exact:true}).click();
   await locate.click();await page.waitForTimeout(400);
   const info=await page.evaluate(()=>({overflow:document.documentElement.scrollWidth>innerWidth+1,canvas:!!document.querySelector('canvas')}));
   assert.equal(info.overflow,false);assert.equal(info.canvas,true);
   await page.screenshot({path:fileURLToPath(new URL(`${width}.png`,out))});
   assert.equal(closedEdges,1);results.push({width,height,closedEdges,...info});
 }
 assert.deepEqual(errors,[]);
 writeFileSync(new URL('result.json',out),JSON.stringify({results,errors},null,2));
 console.log(JSON.stringify({results,errors}));
}finally{await browser.close();}
