import {useEffect,useState} from 'react';
import {Application,Graphics,Rectangle} from 'pixi.js';
import {LogOut} from 'lucide-react';
import {ItemIcon} from './CharacterPanel';
import {drawDistrictFixture,drawInteriorBase} from './districtArt';
import {drawLocalBase,localMarker} from './localSceneArt';
import {terrainModules} from './sceneModules';
import {drawWorldFerry,drawWorldGround,drawWorldMonster} from './worldArt';
import type {Tile} from './types';

const origin={q:0,r:0};
const species:Record<string,string>={'恶霸':'bully','利爪怪':'claw','弓箭手':'archer'};
const pictures=new Map<string,string>();

function draw(g:Graphics,id:string,kind:string,world:boolean,variant:number){
  if(kind==='monsters')drawWorldMonster(g,species[id]??'bully');
  else if(kind==='terrain'){
    if(world)drawWorldGround(g,id,0,0,variant);
    else {
      const cell={...origin,terrain:id,walkable:terrainModules[id]?.surface!=='solid'};
      drawLocalBase(g,cell,true);
      if(terrainModules[id]?.family==='室内'||id==='wall')drawInteriorBase(g,cell);
    }
  }else if(id==='ferry')drawWorldFerry(g);
  else if(id==='shop'||id==='rest')drawDistrictFixture(g,id==='shop'?'shelf':'bed',origin);
  else localMarker(g,origin,id==='gather'?'wood':id==='craft'?'workbench':id==='loot'?'bag':'door');
}

export function useCodexPictures(kind:string,ids:string[],world:boolean,worldTiles?:Tile[]){
  const samples=ids.map(id=>{
    const tile=world?worldTiles?.find(t=>t.terrain===id):null;
    return {id,variant:tile?Math.abs(tile.q*137+tile.r*73)%7:0};
  });
  const signature=JSON.stringify(samples);
  const pageKey=kind+':'+world+':'+signature;
  const [loaded,setLoaded]=useState<{key:string;pictures:Record<string,string>}>({key:'',pictures:{}});
  const [failed,setFailed]=useState(false);
  useEffect(()=>{
    setFailed(false);
    if(kind==='items')return;
    const list=JSON.parse(signature) as typeof samples;
    let disposed=false;
    const keyFor=(id:string,variant:number)=>`${kind}:${world}:${id}:${variant}`;
    async function load(){
      const result:Record<string,string>={};
      const pending=list.filter(({id,variant})=>{
        const cached=pictures.get(keyFor(id,variant));
        if(cached){result[id]=cached;return false;}
        return id!=='leave'||kind!=='components';
      });
      if(pending.length){
        const app=new Application();
        await app.init({width:84,height:76,autoStart:false,antialias:false,backgroundAlpha:0,preference:'webgl'});
        try{
          for(const {id,variant} of pending){
            if(disposed)return;
            const g=new Graphics();
            try{
              draw(g,id,kind,world,variant);
              const frame=kind==='terrain'&&!world?new Rectangle(-37,-34,74,68):new Rectangle(-28,-30,56,58);
              const canvas=app.renderer.extract.canvas({target:g,frame,resolution:1,antialias:false});
              if(!(canvas instanceof HTMLCanvasElement))throw new Error('图鉴图片导出失败');
              const picture=canvas.toDataURL();
              pictures.set(keyFor(id,variant),picture);result[id]=picture;
            }finally{g.destroy();}
          }
        }finally{app.destroy(true,{children:true});}
      }
      if(!disposed)setLoaded({key:pageKey,pictures:result});
    }
    void load().catch(()=>{if(!disposed)setFailed(true);});
    return ()=>{disposed=true;};
  },[kind,world,signature,pageKey]);
  return {ready:loaded.key===pageKey?loaded.pictures:{},failed};
}

export function CodexCardArt({id,kind,picture,failed}:{id:string;kind:string;picture?:string;failed:boolean}){
  if(kind==='items')return <span className="codex-item-icon"><ItemIcon code={id}/></span>;
  if(kind==='components'&&id==='leave')return <span className="codex-item-icon"><LogOut/></span>;
  if(picture)return <img src={picture} alt="" draggable={false}/>;
  return <span className="codex-art-status">{failed?'主图暂不可用':'载入中…'}</span>;
}

