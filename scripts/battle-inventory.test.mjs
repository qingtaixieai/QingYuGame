import assert from 'node:assert/strict';
import {test} from 'node:test';
import {equipmentChanges,equipmentStats,targetReason,itemSupported} from '../frontend/src/battleInventoryRules.ts';
const actor=(id,q,r,hp=10,life='alive')=>({accountId:id,q,r,character:{life,hp,maxHp:20}});
const own=actor('self',0,0),item={code:'bandage',use_action:'bandage',target_mode:'character',use_range:1,heal_amount:4};
test('Bandage accepts self and all six adjacent hexes, not distance two',()=>{
 assert.equal(targetReason(item,own,own),'');
 for(const [q,r] of [[1,0],[0,1],[-1,1],[-1,0],[0,-1],[1,-1]])assert.equal(targetReason(item,own,actor('other',q,r)),'');
 assert.equal(targetReason(item,own,actor('far',2,0)),'目标超出范围');
});
test('Empty, full-health, down and soul targets cannot consume an item',()=>{
 assert(targetReason(item,own,undefined));
 assert(targetReason(item,own,actor('full',1,0,20)));
 for(const life of ['down','soul'])assert(targetReason(item,own,actor('down',1,0,0,life)));
});
test('Self-only definition rejects another character and accepts self',()=>{
 const self={...item,target_mode:'self',use_range:0};
 assert.equal(targetReason(self,own,own),'');assert(targetReason(self,own,actor('other',0,0)));
});
test('Unknown consumables are not routed to bandage or arbitrary endpoints',()=>{
 assert(!itemSupported({...item,code:'potion'}));assert(!itemSupported({...item,use_action:'equipment'}));
});
const equipment=[{code:'axe',damage:3,max_range:1,passive_armor:0},{code:'spear',damage:3,max_range:2,passive_armor:0},{code:'buckler',hand_usage:'off_one',passive_armor:1},{code:'tower_shield',hand_usage:'off_two',passive_armor:2}];
test('Comparison computes actual armor and range changes without unchanged tags',()=>{
 assert.deepEqual(equipmentChanges(equipment,'axe','buckler','axe','buckler'),[]);
 assert.deepEqual(equipmentChanges(equipment,'axe','buckler','spear','').map(x=>[x.key,x.value]),[['armor',-1],['range',1]]);
});
test('Two-hand shield removes attack stats; bare hands retain existing combat values',()=>{
 assert.deepEqual(equipmentStats(equipment,'','tower_shield'),{damage:0,armor:2,range:0});
 assert.deepEqual(equipmentStats(equipment,'',''),{damage:2,armor:0,range:1});
});
