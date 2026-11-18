import type {Hex} from './types';

export type CellSelectionStyle={fill:number;stroke:number};
export const cellKey=(cell:Hex)=>`${cell.q},${cell.r}`;

export function createCellSelection(cells:readonly Hex[],style:CellSelectionStyle){
  const index=new Map(cells.map(cell=>[cellKey(cell),cell]));
  return {cells:[...index.values()],style,contains:(cell:Hex)=>index.has(cellKey(cell))};
}

export type CellSelection=ReturnType<typeof createCellSelection>&{
  onSelect:(cell:Hex)=>void;
  onCancel:()=>void;
  onInvalid?:()=>void;
};

export function selectSceneCell(selection:CellSelection|undefined,cell:Hex){
  if(!selection)return false;
  if(selection.contains(cell))selection.onSelect(cell);
  else selection.onInvalid?.();
  return true;
}
