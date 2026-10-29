import test from 'node:test';
import assert from 'node:assert/strict';
import {tiles,distance,sides,polygon,xy,SIZE} from '../frontend/src/features/battle/battleGeometry.ts';

test('hex tile count and all six boundaries remain consistent',()=>{
  for(const radius of [0,1,3,6,15]){
    const cells=tiles(radius);
    assert.equal(cells.length,1+3*radius*(radius+1));
    assert.equal(new Set(cells.map(h=>`${h.q},${h.r}`)).size,cells.length);
    assert.ok(cells.every(h=>distance({q:0,r:0},h)<=radius));
  }
  assert.deepEqual(sides({q:3,r:-1},3),[0]);
  assert.deepEqual(sides({q:0,r:3},3),[1,2]);
  assert.equal(distance({q:-2,r:1},{q:1,r:-2}),3);
});
test('polygon preserves six screen-space vertices around projected centre',()=>{
  assert.equal(SIZE,36);
  const cell={q:-2,r:1},center=xy(cell);
  const points=polygon(cell).split(' ').map(p=>p.split(',').map(Number));
  assert.equal(points.length,6);
  for(const [x,y] of points)assert.ok(Math.abs(Math.hypot(x-center.x,y-center.y)-(SIZE-1))<1e-9);
});
