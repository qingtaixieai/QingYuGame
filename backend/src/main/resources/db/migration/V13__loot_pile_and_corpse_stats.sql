-- Extend container kinds (corpse/chest/pile) and store corpse six-attribute stats.
-- 'bag' (discard product) is renamed to 'pile' (掉落堆).
ALTER TABLE loot_containers DROP CONSTRAINT loot_containers_kind_check;
UPDATE loot_containers SET kind='pile' WHERE kind='bag';
ALTER TABLE loot_containers ADD CONSTRAINT loot_containers_kind_check CHECK(kind IN ('corpse','chest','pile'));

ALTER TABLE loot_containers ADD COLUMN strength INTEGER NOT NULL DEFAULT 0;
ALTER TABLE loot_containers ADD COLUMN agility INTEGER NOT NULL DEFAULT 0;
ALTER TABLE loot_containers ADD COLUMN constitution INTEGER NOT NULL DEFAULT 0;
ALTER TABLE loot_containers ADD COLUMN intellect INTEGER NOT NULL DEFAULT 0;
ALTER TABLE loot_containers ADD COLUMN perception INTEGER NOT NULL DEFAULT 0;
ALTER TABLE loot_containers ADD COLUMN willpower INTEGER NOT NULL DEFAULT 0;
