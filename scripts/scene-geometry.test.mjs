import test from 'node:test';import assert from 'node:assert/strict';
import {scenePath,sceneSides,sceneHitTest,sceneXY} from '../frontend/src/sceneGeometry.ts';

test('hit test uses rendered hex boundary, not a circular nearest-cell snap',()=>{
 const cells=[{q:0,r:0,terrain:'floor',walkable:true}];
 assert.deepEqual(sceneHitTest(cells,{x:0,y:0}),cells[0]);
 assert.equal(sceneHitTest(cells,{x:0,y:34}),undefined);
 assert.equal(sceneHitTest(cells,{x:30,y:15}),undefined);
 assert.equal(sceneHitTest(cells,{x:100,y:100}),undefined);
});
test('hit test and artwork share coordinates for negative and offset cells',()=>{
 const cells=[{q:-3,r:2,terrain:'floor',walkable:true},{q:4,r:-5,terrain:'wall',walkable:false}];
 for(const c of cells)assert.equal(sceneHitTest(cells,sceneXY(c)),c);
});
test('preview avoids occupied cells and blocked ground',()=>{const cells=[{q:0,r:0},{q:1,r:0},{q:0,r:1},{q:1,r:1},{q:2,r:0}].map(h=>({...h,terrain:'grass',walkable:true}));assert.deepEqual(scenePath(cells,{q:0,r:0},{q:2,r:0},new Set(['1,0'])),[{q:0,r:1},{q:1,r:1},{q:2,r:0}]);cells.find(c=>c.q===1&&c.r===1).walkable=false;assert.deepEqual(scenePath(cells,{q:0,r:0},{q:2,r:0},new Set(['1,0'])),[]);});
test('edge indices match server directions including corners',()=>{assert.deepEqual(sceneSides({q:15,r:-3},15),[0]);assert.deepEqual(sceneSides({q:0,r:15},15),[1,2]);assert.deepEqual(sceneSides({q:0,r:0},15),[]);});
