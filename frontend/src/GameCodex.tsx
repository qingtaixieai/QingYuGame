import {useEffect,useState} from 'react';
import {api} from './api';
import {CodexCardArt,useCodexPictures} from './CodexCardArt';
import {interactionComponents,terrainModules} from './sceneModules';
import type {ProductionState,Recipe} from './shared/production';
import {terrainNames,walkable,type Item,type World} from './types';
import './codex.css';

const tabs=[['components','交互组件'],['items','道具'],['monsters','怪物'],['terrain','地块'],['recipes','配方']] as const;
type Tab=typeof tabs[number][0];
type Entry={name:string;description:string;condition?:string};
type Row=Entry&{key:string};
type Definition=Pick<Item,'code'|'name'|'description'>;
type Creature=Definition;
type Catalog={items:Definition[];creatures:Creature[];recipes:Recipe[]};
const rows=(entries:Record<string,Entry>)=>Object.entries(entries).map(([key,e])=>({...e,key}));
const definitionRows=(entries:Definition[])=>entries.map(({code,...entry})=>({...entry,key:code}));

export function GameCodex({world}:{world:World|null}){
  const [tab,setTab]=useState<Tab>('components');
  const [terrainLayer,setTerrainLayer]=useState<'world'|'scene'>('world');
  const [selected,setSelected]=useState<string|null>(null);
  const [catalog,setCatalog]=useState<Catalog|null>(null);
  const [failed,setFailed]=useState(false),[attempt,setAttempt]=useState(0);
  useEffect(()=>{
    let active=true;
    setFailed(false);
    Promise.all([api<Definition[]>('/items'),api<Creature[]>('/creatures'),api<Pick<ProductionState,'recipes'>>('/production')])
      .then(([items,creatures,{recipes}])=>{if(active)setCatalog({items,creatures,recipes});})
      .catch(()=>{if(active)setFailed(true);});
    return ()=>{active=false;};
  },[attempt]);
  const worldTiles:Row[]=Object.entries(terrainNames).map(([key,name])=>{
    return {key,name,description:key==='river'||key==='ocean'?'水域，无法直接步行通过。':`大世界的${name}地块。`,
      condition:walkable({q:0,r:0,terrain:key,road:false,place:null})?'可通行。':'不可直接通行。'};
  });
  const itemNames=new Map(catalog?.items.map(item=>[item.code,item.name]));
  const recipes:Row[]=catalog?.recipes.map(recipe=>({
    key:recipe.code,name:recipe.name,
    description:Object.entries(recipe.materials).sort(([a],[b])=>a.localeCompare(b)).map(([code,count])=>`${itemNames.get(code)??code} × ${count}`).join('、'),
    condition:`每批产出 ${recipe.output} 个 · ${recipe.duration/1000} 秒`,
  }))??[];
  const entries:Row[]=tab==='components'?rows(interactionComponents):tab==='items'
    ?definitionRows(catalog?.items??[])
    :tab==='monsters'?definitionRows(catalog?.creatures??[])
    :tab==='recipes'?recipes
    :terrainLayer==='world'?worldTiles:rows(terrainModules);
  const usesCatalog=tab==='items'||tab==='monsters'||tab==='recipes';
  const {ready:pictures,failed:artFailed}=useCodexPictures(tab,entries.map(entry=>entry.key),tab==='terrain'&&terrainLayer==='world',world?.tiles);
  const category=tabs.find(([key])=>key===tab)?.[1];
  const active=entries.find(entry=>entry.key===selected);
  function chooseTab(next:Tab){setTab(next);setSelected(null);}
  return <section className="game-codex" aria-label="青屿图鉴">
    <div className="sidebar-heading"><span className="tiny-label">QINGYU CODEX</span><h2>青屿图鉴</h2><p>查阅旅途中会遇到的事物。</p></div>
    <div className="codex-filters" aria-label="图鉴分类">{tabs.map(([key,label])=><button key={key} aria-pressed={tab===key} onClick={()=>chooseTab(key)}>{label}</button>)}</div>
    {tab==='terrain'&&<div className="codex-filters codex-layers" aria-label="地图层级">{([['world','一级地图'],['scene','二三级模块']] as const).map(([key,label])=><button key={key} aria-pressed={terrainLayer===key} onClick={()=>{setTerrainLayer(key);setSelected(null);}}>{label}</button>)}</div>}
    {active&&<article className="codex-entry" aria-label={active.name+'详情'}><h3>{active.name}</h3><p>{active.description||'暂无简介。'}</p>{active.condition&&<small>{active.condition}</small>}</article>}
    <div className="codex-body">
      <ul className="codex-list">{entries.map(entry=><li key={entry.key}>
        <button className="codex-card" data-kind={tab} aria-label={entry.name} aria-pressed={selected===entry.key} onClick={()=>setSelected(entry.key)}>
          <strong className="codex-card-title">{entry.name}</strong>
          <span className="codex-card-art" aria-hidden="true"><CodexCardArt id={entry.key} kind={tab} picture={pictures[entry.key]} failed={artFailed}/></span>
          <span className="codex-card-copy"><small>{category}</small><span>{entry.description||'暂无简介。'}</span></span>
        </button>
      </li>)}</ul>
      {!entries.length&&usesCatalog&&(failed?<div className="empty" role="alert">图鉴数据暂未载入 <button className="soft-button" onClick={()=>setAttempt(n=>n+1)}>重试</button></div>
        :<p className="empty">{catalog?'暂无图鉴条目。':'正在读取图鉴条目…'}</p>)}
    </div>
    <small className="codex-scroll-hint">滚动浏览 · 点卡片查看完整说明</small>
  </section>;
}
