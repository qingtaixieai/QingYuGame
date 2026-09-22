// Isolated headless test browser fallback; no existing browser profile or session is accessed.
import { chromium } from 'file:///C:/Users/lenovo/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs';
import {readFileSync,writeFileSync} from 'node:fs';
const access=readFileSync('../.local/admin-access.txt','utf8');
const password=access.split(/\r?\n/).find(l=>l.startsWith('管理员密码：')).slice('管理员密码：'.length);
const browser=await chromium.launch({channel:'msedge',headless:true});
const page=await browser.newPage({viewport:{width:1440,height:900}});
const errors=[];
page.on('pageerror',e=>errors.push(e.message));
page.on('console',m=>{if(m.type()==='error')errors.push(m.text());});
try {
  await page.goto('https://qingtaixieai.com/',{waitUntil:'networkidle',timeout:30000});
  await page.getByRole('textbox',{name:'用户名',exact:true}).fill('admin');
  await page.getByRole('textbox',{name:'密码',exact:true}).fill(password);
  await page.getByRole('button',{name:'进入青屿大陆',exact:true}).click();
  await page.getByRole('heading',{name:'大陆手记 9',exact:true}).waitFor({timeout:15000});
  await page.locator('canvas').waitFor({timeout:15000});
  await page.getByRole('button',{name:'白石城 城镇 · 7, -5',exact:true}).click();
  await page.getByRole('button',{name:'前往这里',exact:true}).click();
  await page.getByRole('button',{name:'你已在这里',exact:true}).waitFor({timeout:15000});
  await page.getByRole('button',{name:'关闭地点详情',exact:true}).click();
  await page.getByRole('button',{name:'管理后台',exact:true}).click();
  await page.getByRole('dialog',{name:'世界管理',exact:true}).waitFor();
  await page.screenshot({path:'../.local/production-admin.png'});
  await page.getByRole('button',{name:'关闭管理后台',exact:true}).click();
  await page.getByRole('button',{name:'查看全图',exact:true}).click();
  await page.getByRole('button',{name:'关闭提示',exact:true}).click();
  await page.screenshot({path:'../.local/production-preview.png'});
  console.log('PASS production login, canvas rendering, movement and administrator interface');
} finally {
  writeFileSync('../.local/production-browser-errors.json',JSON.stringify(errors,null,2));
  console.log(JSON.stringify({browserErrors:errors},null,2));
  await page.screenshot({path:'../.local/production-last-state.png'}).catch(()=>{});
  await browser.close();
}
