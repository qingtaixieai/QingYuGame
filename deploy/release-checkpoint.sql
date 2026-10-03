-- Read-only release fingerprints. Movement during release can change positions naturally.
SELECT 'world',md5(document) FROM worlds WHERE id=1;
SELECT 'accounts',count(*)::text FROM accounts;
SELECT 'characters',count(*)::text FROM characters;
SELECT 'positions',md5(coalesce(string_agg(id::text || ':' || q || ',' || r,'|' ORDER BY id),'')) FROM accounts;
SELECT 'ferry',world_version || ':' || built::text || ':' || wood || ':' || stone FROM ferry_state WHERE id=1;
SELECT 'schema',version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;
