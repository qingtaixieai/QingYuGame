import {Compass,Expand,LocateFixed,Minus,Plus} from 'lucide-react';
import {MapTools} from './MapTools';

export function MapChrome({title,subtitle,zoom,onZoom,onFit,onLocate,onMessage,toolsClass=''}:{
  title:string;subtitle:string;zoom:number;onZoom:(factor:number)=>void;onFit:()=>void;
  onLocate:()=>void;onMessage:(message:string)=>void;toolsClass?:string;
}){
  return <>
    <div className="map-title"><span className="map-title-icon"><Compass size={20}/></span><div><strong>{title}</strong><small>{subtitle}</small></div></div>
      <div className="compass" aria-hidden="true"><span>N</span><svg viewBox="0 0 50 50"><path d="M25 4L33 34 25 28 17 34Z" fill="#446d64"/><path d="M25 4v24l-8 6z" fill="#faf6df"/></svg></div>
      <MapTools className={`map-tools ${toolsClass}`} onMessage={onMessage}><button aria-label="放大地图" title={`放大地图 · 当前 ${zoom}%`} onClick={()=>onZoom(1.3)}><Plus size={19}/></button><button aria-label="缩小地图" title="缩小" onClick={()=>onZoom(1/1.3)}><Minus size={19}/></button><hr/><button aria-label="查看全图" title="查看全图" onClick={onFit}><Expand size={18}/></button><button aria-label="定位自己" title="定位自己" onClick={onLocate}><LocateFixed size={19}/></button></MapTools>
  </>;
}
