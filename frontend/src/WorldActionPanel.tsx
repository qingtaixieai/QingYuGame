import type {ReactNode} from 'react';

export function WorldActionPanel({label,title,subtitle,icon,headerAction,children,className=''}:{
  label:string;title:string;subtitle:string;icon:ReactNode;headerAction?:ReactNode;children:ReactNode;className?:string;
}){
  return <section className={`ferry-panel ${className}`} aria-label={label}>
    <header>{icon}<div><strong>{title}</strong><small>{subtitle}</small></div>{headerAction}</header>
    {children}
  </section>;
}
