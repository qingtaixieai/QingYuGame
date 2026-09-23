import pg from 'pg';import {readFileSync,writeFileSync} from 'node:fs';import assert from 'node:assert/strict';
const fixture=JSON.parse(readFileSync(new URL('../.local/battle-visual.json',import.meta.url),'utf8')),db=new pg.Client({host:'127.0.0.1',port:55432,user:'worldgame',database:'worldgame'});await db.connect();
const account=fixture.users[1].data.id;
if(process.argv[2]==='prepare')await db.query('update battle_actors set q=6,r=-3,withdraw_direction=0 where encounter_id=$1 and account_id=$2',[fixture.battle,account]);
const actor=(await db.query('select q,r,initiative,entry_round,withdraw_direction from battle_actors where encounter_id=$1 and account_id=$2',[fixture.battle,account])).rows[0];
const battle=(await db.query('select round_number,turn_account_id,turn_points,turn_start_edge from battle_encounters where id=$1',[fixture.battle])).rows[0];
const file=new URL('../.local/battle-edges-restart.json',import.meta.url);
if(process.argv[2]==='prepare'){writeFileSync(file,JSON.stringify({actor,battle}));console.log('Withdrawal and turn checkpoint saved');}else{assert.deepEqual({actor,battle},JSON.parse(readFileSync(file,'utf8')));await db.query('update battle_actors set withdraw_direction=null where encounter_id=$1 and account_id=$2',[fixture.battle,account]);console.log('PASS withdrawal, position, initiative and current turn survive server restart');}
await db.end();
