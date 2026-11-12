import {Children,useId,useState,type ReactNode} from 'react';
import {ChevronDown,ChevronUp,X} from 'lucide-react';
import './placeActions.css';
import type {ArrangedInteraction} from './cellInteractions';

type Props={
  title:string;
  subtitle?:string;
  children?:ReactNode;
  primary?:ReactNode;
  secondary?:ReactNode;
  onClose?:()=>void;
  className?:string;
  detailsExpanded?:boolean;
  onToggleDetails?:()=>void;
};

export function PlaceActions({title,subtitle,children,primary,secondary,onClose,className='',detailsExpanded,onToggleDetails}:Props){
  const [expanded,setExpanded]=useState(true);
  const showDetails=detailsExpanded??expanded;
  const detailsId=useId();
  const details=Children.toArray(children);
  return <section className={`place-actions ${className}`} aria-label="地点操作">
    <header>
      <div><h3>{title}</h3>{subtitle&&<p aria-live="polite">{subtitle}</p>}</div>
      {details.length>0&&<button type="button" className="place-actions-toggle" aria-controls={detailsId} aria-expanded={showDetails}
        aria-label={showDetails?'收起地点说明':'展开地点说明'} onClick={onToggleDetails??(()=>setExpanded(value=>!value))}>{showDetails?<ChevronDown size={17}/>:<ChevronUp size={17}/>}</button>}
      {onClose&&<button type="button" aria-label="取消选择" onClick={onClose}><X size={17}/></button>}
    </header>
    {details.length>0&&<div className="place-actions-detail" id={detailsId} hidden={!showDetails}>{details}</div>}
    {(primary||secondary)&&<div className="place-actions-buttons">
      {primary&&<div className="place-actions-primary">{primary}</div>}
      {secondary&&<div className="place-actions-secondary">{secondary}</div>}
    </div>}
  </section>;
}

export function PlaceComponents({items,selected,onSelect}:{
  items:readonly ArrangedInteraction[];
  selected?:string|null;
  onSelect:(id:string)=>void;
}){
  if(items.length<2)return null;
  return <div className="place-components" role="group" aria-label="格内组件">
    {items.map(item=><button key={item.id} type="button" className="place-secondary"
      aria-pressed={selected===item.id} onClick={()=>onSelect(item.id)}>{item.number} · {item.label}</button>)}
  </div>;
}
