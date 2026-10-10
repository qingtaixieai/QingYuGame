export type Hex={q:number;r:number};
export type LootContainer=Hex&{id:string;kind:'corpse'|'chest'|'pile';name:string;sourceKind:'monster'|'player'|null;sourceId:string|null;version:string;battleId:string|null;battleQ:number|null;battleR:number|null;carrierId:string|null;createdAt:number;expiresAt:number|null;itemCount:number;strength:number;agility:number;constitution:number;intellect:number;perception:number;willpower:number};
export type Tile=Hex&{terrain:string;road:boolean;place:null|{name:string;type:string;description:string}};
export type World={version:string;seed:number;name:string;radius:number;spawn:Hex;tiles:Tile[]};
export type Account=Hex&{id:string;username:string;approved:boolean;admin:boolean;color:string};
export type Player=Hex&{id:string;username:string;color:string;online:boolean;moving:boolean;inBattle:boolean;kind:string;species:string|null;life:string;hp:number;maxHp:number};
export type Item={code:string;name:string;quantity:number;description:string;kind:string;shape:string;min_range:number;max_range:number;attack_cost:number;damage:number;moveRule:string;hand_usage:string;passive_armor:number;guard_reduction:number;guard_min_damage:number;grants_attack:boolean;use_action?:string|null;target_mode?:'self'|'character'|null;use_range?:number|null;heal_amount?:number|null};
export type Weapon={code:string;name:string;shape:string;minRange:number;maxRange:number;cost:number;damage:number;moveRule:string};
export type Character=Hex&{id:string;kind:string;species:string|null;name:string;life:'alive'|'down'|'soul'|'dead';hp:number;maxHp:number;downHp:number;maxDownHp:number;strength:number;agility:number;constitution:number;intellect:number;perception:number;willpower:number;weapon:string|null;offhand:string|null;body:string|null;bindQ:number;bindR:number;deathQ:number|null;deathR:number|null;timerKind:string|null;timerEnd:number;protectedUntil:number};
export type ResourceNode=Hex&{id:string;kind:'wood'|'stone';readyAt:number};
export type WorldAction=Hex&{accountId:string;kind:string;nodeId:string;startedAt:number;endsAt:number};
export type Emote={accountId:string;code:string;startedAt:number;expiresAt:number};
export type BattleSummary=Hex&{id:string;participants:number};
export type Loadout={body:string|null;mainHand:string|null;offHand:string|null;weapon:Weapon;offhand:Item|null;armor:number;canGuard:boolean;guardReduction:number;guardMinDamage:number};
export type BattleActor=Hex&{accountId:string;username:string;color:string;initiative:number;entryRound:number;online:boolean;withdrawDirection:number|null;character:Character;weapon:Weapon;loadout:Loadout;initiativeRoll:number;initiativeScore:number;reactionPoints:number;shieldRaised:boolean;actions:string[]};
export type BattleIntent=Hex&{kind:'melee'|'aim';ready:boolean;attackerId:string;targetId:string|null;visibility:'public'|'attacker_only';cells:Hex[];damage:number;minRange:number;maxRange:number;moveRule:string;originQ:number|null;originR:number|null};
export type BattleEvent={actorName?:string;targetName?:string;amount?:number;blocked?:number;id:number;kind:string;actorId:string|null;targetId:string|null;q:number|null;r:number|null;happenedAt:number};
export type CombatOption={definition:{id:string;source:string;name:string;effect:string;cost:number;damage:number;description:string};disabledReason:string|null;targets:string[];cells:Hex[]};
export type BattleState={active:false}|{active:true;actionOptions:CombatOption[];id:string;worldQ:number;worldR:number;radius:number;round:number;turnAccountId:string;turnPoints:number;turnDeadline:number;attackUsed:boolean;actors:BattleActor[];intents:BattleIntent[];events:BattleEvent[];edges:BattleEdge[];turnStartEdge:boolean};
export type State={character:Character;inventory:Item[];ferry:FerryState|null;type:'state';version:string;players:Player[];tick:number;emotes:Emote[];resources:ResourceNode[];actions:WorldAction[];serverTime:number;battles:BattleSummary[];battleRevision:number};
export const terrainNames:Record<string,string>={plain:'平原',forest:'森林',hill:'丘陵',mountain:'高山',ocean:'海洋',beach:'海岸',river:'河流',bridge:'桥梁'};
export const placeNames:Record<string,string>={town:'城镇',village:'村庄',ruin:'遗迹',port:'港口',landing:'停靠点'};
export const key=(h:Hex)=>`${h.q},${h.r}`;
export const walkable=(t:Tile)=>!['ocean','mountain','river'].includes(t.terrain);

export type BattleEdge=Hex&{direction:number;name:string;walkable:boolean;battleId:string|null;destination:string};

export type FerryState=Hex&{wood:number;stone:number;woodNeeded:number;stoneNeeded:number;built:boolean;phase:'mainland'|'outbound'|'island'|'inbound';nextAt:number;dwellMs:number;travelMs:number;mainlandPort:Hex;islandLanding:Hex;route:Hex[];passengerIds:string[]};
