import type {Hex,Tile} from './protocol';
export const terrainNames:Record<string,string>={plain:'平原',forest:'森林',hill:'丘陵',mountain:'高山',ocean:'海洋',beach:'海岸',river:'河流',bridge:'桥梁'};
export const placeNames:Record<string,string>={town:'城镇',village:'村庄',ruin:'遗迹',port:'港口',landing:'停靠点'};
export const key=(h:Hex)=>`${h.q},${h.r}`;
export const walkable=(t:Tile)=>!['ocean','mountain','river'].includes(t.terrain);
