// Compatibility entry for existing consumers; protocol ownership lives in shared/.
export type * from './shared/protocol';
export {terrainNames,placeNames,key,walkable} from './shared/worldPresentation';
