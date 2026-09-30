import {useEffect,useState} from 'react';
import {TreePine,Maximize,Minimize} from 'lucide-react';
export function MapTools({children,className,onMessage}:{children:React.ReactNode;className:string;onMessage?:(s:string)=>void}){
 const [open,setOpen]=useState(false),[full,setFull]=useState(false);
 useEffect(()=>{const changed=()=>setFull(!!document.fullscreenElement);document.addEventListener('fullscreenchange',changed);return()=>document.removeEventListener('fullscreenchange',changed);},[]);
 async function fullscreen(){try{if(document.fullscreenElement)await document.exitFullscreen();else if(document.documentElement.requestFullscreen)await document.documentElement.requestFullscreen();else{onMessage?.('当前浏览器不支持全屏，可添加到主屏幕后打开。');return;}setFull(!!document.fullscreenElement);}catch{onMessage?.('当前浏览器未允许全屏，可添加到主屏幕后打开。');}}
 return <div className={className+' map-tool-group '+(open?'tools-open':'')}><button className="qingyu-tool-toggle" aria-label={open?'收起地图工具':'展开地图工具'} aria-expanded={open} onClick={()=>setOpen(v=>!v)}><TreePine size={22}/></button><div className="map-tool-items">{children}<button aria-label="切换全屏" title="切换全屏" onClick={()=>void fullscreen()}>{full?<Minimize size={18}/>:<Maximize size={18}/>}</button></div></div>;
}
