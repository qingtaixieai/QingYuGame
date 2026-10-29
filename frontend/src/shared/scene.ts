import type {Hex} from './protocol';
export type SceneCell=Hex&{terrain:string;walkable:boolean};
export type ScenePosition=Hex&{characterId:string;sceneId:string};
export type SceneResident=Hex&{id:string;username:string;life:string;hp:number;maxHp:number;kind:string};
export type SceneDoor={id:string;name:string;cell:Hex;destinationScene:string;destination:Hex;open:boolean};
export type SceneMap={level:number;id:string;worldVersion:string;name:string;radius:number;owner:Hex;cells:SceneCell[]};
