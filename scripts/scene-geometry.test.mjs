import test from 'node:test';import assert from 'node:assert/strict';
import {scenePath,sceneSides} from '../frontend/src/sceneGeometry.ts';
test('preview avoids occupied cells and blocked ground',()=>{const cells=[{q:0,r:0},{q:1,r:0},{q:0,r:1},{q:1,r:1},{q:2,r:0}].map(h=>({...h,terrain:'grass',walkable:true}));assert.deepEqual(scenePath(cells,{q:0,r:0},{q:2,r:0},new Set(['1,0'])),[{q:0,r:1},{q:1,r:1},{q:2,r:0}]);cells.find(c=>c.q===1&&c.r===1).walkable=false;assert.deepEqual(scenePath(cells,{q:0,r:0},{q:2,r:0},new Set(['1,0'])),[]);});
test('edge indices match server directions including corners',()=>{assert.deepEqual(sceneSides({q:15,r:-3},15),[0]);assert.deepEqual(sceneSides({q:0,r:15},15),[1,2]);assert.deepEqual(sceneSides({q:0,r:0},15),[]);});
