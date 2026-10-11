ALTER TABLE loot_containers ADD COLUMN source_species TEXT;
UPDATE loot_containers l SET source_species=c.species FROM characters c WHERE l.source_id=c.id;
