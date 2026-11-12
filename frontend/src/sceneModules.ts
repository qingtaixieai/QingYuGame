import {edgeKey,sceneKey,type SceneCell} from './sceneGeometry';
import type {BlockedEdge,Hex} from './types';

type Entry={name:string;description:string;condition?:string;icon:string};
type TerrainModule=Entry&{family:'野外'|'室内'|'边界';surface:'open'|'road'|'indoor'|'water'|'solid'};
export const terrainModules:Record<string,TerrainModule>={
  grass:{name:'草地',description:'野外通行地面。',family:'野外',surface:'open',icon:'grass'},
  forest:{name:'林地',description:'用于林带与林缘过渡。',family:'野外',surface:'open',icon:'forest'},
  dirt:{name:'土路',description:'连接野外区域的地面。',family:'野外',surface:'road',icon:'road'},
  paving:{name:'石板路',description:'用于街道和建筑门前。',family:'野外',surface:'road',icon:'road'},
  street:{name:'街道',description:'连续的城镇通路。',family:'野外',surface:'road',icon:'road'},
  plaza:{name:'广场',description:'供旅人通行的开阔空间。',family:'野外',surface:'road',icon:'plaza'},
  sand:{name:'沙地',description:'位于陆地和水面之间。',family:'野外',surface:'open',icon:'sand'},
  rock:{name:'岩地',description:'岩层地面，部分岩块会阻挡移动。',family:'野外',surface:'open',icon:'rock'},
  bridge:{name:'桥面',description:'跨越水面的通行地面。',family:'野外',surface:'road',icon:'road'},
  shallow:{name:'浅水',description:'浅水区域，通行以格子详情为准。',family:'边界',surface:'water',icon:'water'},
  deep:{name:'深水',description:'深水区域，无法步行通过。',family:'边界',surface:'water',icon:'water'},
  building:{name:'建筑占地',description:'建筑覆盖的不可通行区域。',family:'边界',surface:'solid',icon:'house'},
  wall:{name:'墙体',description:'整个墙格无法通行。',family:'边界',surface:'solid',icon:'wall'},
  floor:{name:'木地板',description:'室内通行地面。',family:'室内',surface:'indoor',icon:'floor'},
  lobby:{name:'门厅',description:'入口附近的室内通行区域。',family:'室内',surface:'indoor',icon:'floor'},
  lounge:{name:'休息区',description:'供旅人停留的室内区域。',family:'室内',surface:'indoor',icon:'floor'},
  reception:{name:'柜台区',description:'门厅通往柜台的室内区域。',family:'室内',surface:'indoor',icon:'floor'},
  rug:{name:'地毯',description:'可通行的室内铺面。',family:'室内',surface:'indoor',icon:'floor'},
  bed:{name:'床铺',description:'占据完整格子的室内陈设。',family:'室内',surface:'solid',icon:'house'},
  shelf:{name:'货架',description:'占据完整格子的陈列家具。',family:'室内',surface:'solid',icon:'house'},
  furniture:{name:'家具',description:'占据格子的室内陈设。',family:'室内',surface:'solid',icon:'house'},
  'inn-counter':{name:'旅店柜台',description:'沿墙放置的室内陈设，墙格不可通行。',family:'室内',surface:'solid',icon:'house'},
};
export const interactionComponents:Record<string,Entry>={
  door:{name:'门',description:'在室内与室外之间通行。',condition:'站到门口，门开启且角色已停下。',icon:'door'},
  leave:{name:'地块出口',description:'返回大世界或跨入相邻地块。',condition:'角色已停下，跨越时需站在可通行的边缘。',icon:'door'},
  ferry:{name:'公共渡船',description:'在大陆和远屿间往返。未建成时可投入材料。',condition:'到达对应港口，登船和下船需要渡船靠岸。',icon:'ship'},
  gather:{name:'采集点',description:'采集木头、石头等资源。',condition:'到达采集格；资源需可采集，部分资源需要合适工具。',icon:'forest'},
  craft:{name:'制作设施',description:'使用配方制作物品。',condition:'到设施旁边并准备配方所需材料。',icon:'hammer'},
  shop:{name:'商店柜台',description:'用铜币购买和出售物品。',condition:'进入营业的商店。',icon:'shop'},
  rest:{name:'旅店休整',description:'休整以恢复生命。',condition:'进入旅店并满足休整条件。',icon:'bed'},
  loot:{name:'遗留物',description:'查看并拾取地上的掉落物。',condition:'到达遗留物所在格。',icon:'bag'},
};
const neighbors=[{q:1,r:0},{q:0,r:1},{q:-1,r:1},{q:-1,r:0},{q:0,r:-1},{q:1,r:-1}];

/** Stamp a module relative to its anchor. The scene's server-provided cells stay authoritative. */
type ModuleTile=Hex&{terrain:string};
export const terrainStamps={
  plaza:[{q:0,r:0},...neighbors,...neighbors.map(({q,r})=>({q:q*2,r:r*2})),
    ...neighbors.map(({q,r},i)=>({q:q+neighbors[(i+1)%6].q,r:r+neighbors[(i+1)%6].r}))].map(offset=>({...offset,terrain:'plaza'})),
  lobby:[{q:0,r:0,terrain:'lobby'},{q:0,r:-1,terrain:'lobby'},{q:1,r:-1,terrain:'lobby'},{q:-1,r:0,terrain:'lobby'}],
} satisfies Record<string,ModuleTile[]>;
export function stampModule(target:Map<string,string>,anchor:Hex,tiles:readonly ModuleTile[],allowed:ReadonlySet<string>){
  for(const tile of tiles){
    const key=sceneKey({q:anchor.q+tile.q,r:anchor.r+tile.r});
    if(allowed.has(key))target.set(key,tile.terrain);
  }
}

/** A shared edge connects only when both modules accept that surface and the edge is open. */
export function moduleSeams(cells:SceneCell[],edges:readonly BlockedEdge[]){
  const index=new Map(cells.map(cell=>[sceneKey(cell),cell]));
  const sealed=new Set(edges.map(edge=>edgeKey(edge.from,edge.to)));
  const seams:{from:SceneCell;to:SceneCell;surface:'road'|'indoor'}[]=[];
  for(const from of cells){
    const surface=terrainModules[from.terrain]?.surface;
    if(!from.walkable||(surface!=='road'&&surface!=='indoor'))continue;
    for(const offset of neighbors.slice(0,3)){
      const to=index.get(sceneKey({q:from.q+offset.q,r:from.r+offset.r}));
      if(to?.walkable&&terrainModules[to.terrain]?.surface===surface&&!sealed.has(edgeKey(from,to)))seams.push({from,to,surface});
    }
  }
  return seams;
}

