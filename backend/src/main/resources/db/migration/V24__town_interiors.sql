CREATE TABLE town_buildings (
 id UUID PRIMARY KEY REFERENCES scene_documents(id) ON DELETE CASCADE,
 outdoor_id UUID NOT NULL REFERENCES scene_documents(id) ON DELETE CASCADE,
 kind TEXT NOT NULL CHECK(kind IN ('shop','workshop','smith','inn')),
 npc_id UUID REFERENCES characters(id) ON DELETE SET NULL,
 UNIQUE(outdoor_id,kind)
);
CREATE TABLE town_doors (
 portal_id UUID PRIMARY KEY REFERENCES scene_portals(id) ON DELETE CASCADE,
 building_id UUID NOT NULL REFERENCES town_buildings(id) ON DELETE CASCADE
);
CREATE TABLE rest_payments (
 actor_id UUID PRIMARY KEY REFERENCES production_tasks(actor_id) ON DELETE CASCADE,
 copper BIGINT NOT NULL CHECK(copper>=0),silver BIGINT NOT NULL CHECK(silver>=0),gold BIGINT NOT NULL CHECK(gold>=0),
 change_copper BIGINT NOT NULL CHECK(change_copper>=0)
);
DROP INDEX battle_one_active_per_world_cell;
CREATE UNIQUE INDEX battle_one_active_scene ON battle_encounters(scene_id) WHERE active AND scene_id IS NOT NULL;
CREATE UNIQUE INDEX battle_one_active_legacy_cell ON battle_encounters(world_version,world_q,world_r) WHERE active AND scene_id IS NULL;
ALTER TABLE production_tasks DROP CONSTRAINT production_tasks_kind_check;
ALTER TABLE production_tasks ADD CHECK(kind IN ('craft','gather','skin','rest'));
