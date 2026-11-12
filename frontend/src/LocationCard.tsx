import type {ReactNode} from 'react';
import {X} from 'lucide-react';

export function LocationCard({icon,subtitle,title,onClose,children,className=''}:{
  icon:ReactNode;subtitle:string;title:string;onClose:()=>void;children:ReactNode;className?:string;
}){
  return <section className={`location-card ${className}`} aria-label="地点详情">
    <div className="location-heading">
      <span className="place-icon">{icon}</span>
      <div><span className="tiny-label">{subtitle}</span><h3>{title}</h3></div>
      <button aria-label="关闭地点详情" className="icon-button" onClick={onClose}><X size={17}/></button>
    </div>
    {children}
  </section>;
}
