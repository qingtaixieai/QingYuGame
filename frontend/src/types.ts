export type Hex={q:number;r:number};
export type Tile=Hex&{terrain:string;road:boolean;place:null|{name:string;type:string;description:string}};
export type World={version:string;seed:number;name:string;radius:number;spawn:Hex;tiles:Tile[]};
export type Account=Hex&{id:string;username:string;approved:boolean;admin:boolean;color:string};
export type Player=Hex&{id:string;username:string;color:string;online:boolean;moving:boolean;inBattle:boolean};
export type Item={code:string;name:string;quantity:number};
export type ResourceNode=Hex&{id:string;kind:'wood'|'stone';readyAt:number};
export type WorldAction=Hex&{accountId:string;kind:string;nodeId:string;startedAt:number;endsAt:number};
export type Emote={accountId:string;code:string;startedAt:number;expiresAt:number};
export type BattleSummary=Hex&{id:string;participants:number};
export type BattleActor=Hex&{accountId:string;username:string;color:string;initiative:number;entryRound:number;online:boolean;withdrawDirection:number|null};
export type BattleIntent=Hex&{attackerId:string;targetId:string|null;visibility:'public'|'attacker_only'};
export type BattleEvent={id:number;kind:string;actorId:string|null;targetId:string|null;q:number|null;r:number|null;happenedAt:number};
export type BattleState={active:false}|{active:true;id:string;worldQ:number;worldR:number;radius:number;round:number;turnAccountId:string;turnPoints:number;turnDeadline:number;attackUsed:boolean;actors:BattleActor[];intents:BattleIntent[];events:BattleEvent[];edges:BattleEdge[];turnStartEdge:boolean};
export type State={ferry:FerryState;type:'state';version:string;players:Player[];tick:number;emotes:Emote[];resources:ResourceNode[];actions:WorldAction[];serverTime:number;battles:BattleSummary[];battleRevision:number};
export const terrainNames:Record<string,string>={plain:'平原',forest:'森林',hill:'丘陵',mountain:'高山',ocean:'海洋',beach:'海岸',river:'河流',bridge:'桥梁'};
export const placeNames:Record<string,string>={town:'城镇',village:'村庄',ruin:'遗迹',port:'港口',landing:'停靠点'};
export const key=(h:Hex)=>`${h.q},${h.r}`;
export const walkable=(t:Tile)=>!['ocean','mountain','river'].includes(t.terrain);

export type BattleEdge=Hex&{direction:number;name:string;walkable:boolean;battleId:string|null;destination:string};

export type FerryState=Hex&{wood:number;stone:number;woodNeeded:number;stoneNeeded:number;built:boolean;phase:'mainland'|'outbound'|'island'|'inbound';nextAt:number;dwellMs:number;travelMs:number;mainlandPort:Hex;islandLanding:Hex;route:Hex[];passengerIds:string[]};
