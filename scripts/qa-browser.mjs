// Isolated browser context; never reads the user's personal browser profile.
import {existsSync,writeFileSync,mkdirSync} from 'node:fs';
import {join} from 'node:path';
import {pathToFileURL} from 'node:url';
const fallback=join(process.env.USERPROFILE||'', '.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs');
const runtime=process.env.QA_PLAYWRIGHT_MODULE||(existsSync(fallback)?fallback:null);
const {chromium}=await import(runtime?pathToFileURL(runtime).href:'playwright');
const url=process.argv[2]||'http://127.0.0.1:5173';
const out=process.argv[3];
if(!out)throw new Error('Output directory required');
mkdirSync(out,{recursive:true});
const browser=await chromium.launch({channel:process.env.QA_BROWSER_CHANNEL||'msedge',headless:process.env.QA_HEADED!=='1'});
const reports=[],errors=[];
try{
 const context=await browser.newContext({storageState:process.env.QA_STORAGE_STATE||undefined});
 const page=await context.newPage();
 page.on('pageerror',e=>errors.push({kind:'pageerror',message:e.message.slice(0,200)}));
 page.on('console',m=>{if(m.type()==='error')errors.push({kind:'console',message:m.text().slice(0,200)});});
 await page.goto(url,{waitUntil:'domcontentloaded',timeout:30000});
 if(process.env.QA_HEADED==='1')await page.waitForTimeout(15000); // User may log in/board before the sample.
 await page.waitForTimeout(1500);
 const inGame=await page.locator('canvas').count()>0;
 async function inspect(name){
  const result=await page.evaluate(()=>{
   const width=innerWidth,height=innerHeight,controls=[];
   for(const el of document.querySelectorAll('button,input,textarea,select')){
    const r=el.getBoundingClientRect(),s=getComputedStyle(el);
    if(r.width<=0||r.height<=0||s.visibility==='hidden'||s.display==='none')continue;
    // Offscreen/occluded controls are candidates only: a scroll container may intentionally hide them.
    const label=el.getAttribute('aria-label')||el.getAttribute('title')||el.tagName;
    if(r.right<0||r.bottom<0||r.left>width||r.top>height)continue;
    const x=Math.min(width-1,Math.max(0,r.left+r.width/2)),y=Math.min(height-1,Math.max(0,r.top+r.height/2));
    const top=document.elementFromPoint(x,y);
    if(r.left<0||r.right>width||r.top<0||r.bottom>height||top&&top!==el&&!el.contains(top))controls.push(label);
   }
   return {horizontalOverflow:document.documentElement.scrollWidth>width+1,reviewControls:[...new Set(controls)].slice(0,30)};
  });
  await page.screenshot({path:join(out,name+'.png')});
  reports.push({name,...result});
 }
 for(const [width,height] of [[1280,800],[844,390]]){
  await page.setViewportSize({width,height});
  await page.waitForTimeout(350);
  await inspect(`${width}-current`);
  if(inGame){
   for(const name of ['聊天室','人物']){
    const button=page.getByRole('button',{name,exact:true}).first();
    if(await button.isVisible().catch(()=>false)){
     await button.click();await page.waitForTimeout(350);await inspect(`${width}-${name}`);
    }else reports.push({name:`${width}-${name}`,status:'skipped',reason:'entry not visible'});
   }
  }
 }
 const performance=await page.evaluate(async()=>{
  const frames=[],tasks=[];let previous,handle;
  const observer=PerformanceObserver.supportedEntryTypes.includes('longtask')?new PerformanceObserver(list=>tasks.push(...list.getEntries().map(x=>x.duration))):null;
  observer?.observe({type:'longtask'});
  function frame(now){if(previous!==undefined)frames.push(now-previous);previous=now;handle=requestAnimationFrame(frame);}
  handle=requestAnimationFrame(frame);
  await new Promise(resolve=>setTimeout(resolve,10000));cancelAnimationFrame(handle);observer?.disconnect();
  frames.sort((a,b)=>a-b);
  return {sampleMs:10000,frames:frames.length,p95Ms:frames[Math.floor(frames.length*.95)]??null,maxMs:frames.at(-1)??null,over50Ms:frames.filter(x=>x>50).length,longTasks:tasks.length,longTaskTotalMs:tasks.reduce((a,b)=>a+b,0),longTaskSupported:!!observer,visibility:document.visibilityState};
 });
 const result={inGame,coverage:inGame?'current game scene plus available sidebar panels':'login page only; game, battle and sailing NOT verified',reports,performance,errors,note:'Desktop viewport simulation. Occlusion candidates require screenshot review. Sailing and battle require an already prepared scene; no game actions are automated.'};
 writeFileSync(join(out,'browser.json'),JSON.stringify(result,null,2));
 console.log(JSON.stringify({inGame,pages:reports.length,errors:errors.length,performance}));
 if(!inGame)process.exitCode=2;
 else if(errors.length||reports.some(x=>x.horizontalOverflow))process.exitCode=1;
}finally{await browser.close();}
