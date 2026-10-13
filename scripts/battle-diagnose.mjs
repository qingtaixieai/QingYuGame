// Explicit connection only. The transaction enforces read-only access even with an admin account.
import pg from 'pg';
import {writeFileSync} from 'node:fs';
if(!process.env.QA_DATABASE_URL){console.error('BLOCKED: set QA_DATABASE_URL to the diagnostic database connection; no credentials inferred.');process.exit(2);}
const client=new pg.Client({connectionString:process.env.QA_DATABASE_URL,connectionTimeoutMillis:5000,statement_timeout:5000});
try{
 await client.connect();
 await client.query('BEGIN READ ONLY');
 const {rows}=await client.query(`SELECT b.id,b.round_number,b.turn_account_id,b.turn_points,b.turn_deadline,
  EXISTS(SELECT 1 FROM battle_actors a WHERE a.encounter_id=b.id AND a.account_id=b.turn_account_id) actor_present,
  (SELECT count(*)::int FROM battle_actors a WHERE a.encounter_id=b.id) actors,
  (SELECT max(happened_at) FROM battle_events e WHERE e.encounter_id=b.id) last_event_at,
  (extract(epoch from clock_timestamp())*1000)::bigint sampled_at
  FROM battle_encounters b WHERE active=true ORDER BY turn_deadline LIMIT 100`);
 const battles=rows.map(r=>({...r,overdueMs:Math.max(0,Number(r.sampled_at)-Number(r.turn_deadline))}));
 const result={sampledAt:rows.length?Number(rows[0].sampled_at):Date.now(),battles,note:'Read-only snapshot, max 100 encounters. Repeated overdue samples are evidence to investigate, not a proven cause.'};
 writeFileSync(process.argv[2]||'battle-diagnostic.json',JSON.stringify(result,null,2));
 const suspicious=battles.filter(r=>r.overdueMs>5000||!r.actor_present||r.turn_points<0);
 console.log(JSON.stringify({active:battles.length,suspicious:suspicious.map(r=>r.id)}));
 if(suspicious.length)process.exitCode=1;
 await client.query('ROLLBACK');
}catch(e){console.error('Battle diagnostic failed:',e.code||e.name);process.exitCode=1;}
finally{await client.end().catch(()=>{});}
