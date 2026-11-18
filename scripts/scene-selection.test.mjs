import test from 'node:test';
import assert from 'node:assert/strict';
import {createCellSelection,selectSceneCell} from '../frontend/src/sceneSelection.ts';

test('a selection draws and accepts the same unique cells',()=>{
  const first={q:1,r:0},second={q:2,r:0};
  const result=createCellSelection([first,{...first},second],{fill:0xff0000,stroke:0xffffff});
  assert.deepEqual(result.cells,[first,second]);
  assert.ok(result.contains({...first}));
  assert.equal(result.contains({q:3,r:0}),false);
  const calls=[];
  const selection={...result,onSelect:cell=>calls.push(cell),onInvalid:()=>calls.push('invalid'),onCancel:()=>calls.push('cancel')};
  assert.equal(selectSceneCell(selection,{q:3,r:0}),true);
  assert.equal(selectSceneCell(selection,{...second}),true);
  assert.equal(selectSceneCell(undefined,first),false);
  assert.deepEqual(calls,['invalid',second]);
});
