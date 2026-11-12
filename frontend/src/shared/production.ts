export type SceneObject={id:string;kind:'resource'|'facility';code:string;name:string;q:number;r:number;depleted:boolean;inUse:boolean};
export type Recipe={code:string;name:string;materials:Record<string,number>;facility:string;duration:number;output:number;batch:boolean};
export type Task={kind:string;recipe:string|null;startedAt:number;dueAt:number;total:number;completed:number};
export type ProductionState={recipes:Recipe[];task:Task|null;reserved:{item_code:string;quantity:number}[];notice?:{message:string;created_at:number}|null;skinTargets?:{id:string;name:string}[]};
