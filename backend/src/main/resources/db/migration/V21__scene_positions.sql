CREATE TABLE scene_positions (
 character_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
 scene_id UUID NOT NULL REFERENCES scene_documents(id) ON DELETE CASCADE,
 q INTEGER NOT NULL, r INTEGER NOT NULL,
 active BOOLEAN NOT NULL DEFAULT FALSE,
 PRIMARY KEY(character_id,scene_id)
);
CREATE UNIQUE INDEX scene_one_active_position ON scene_positions(character_id) WHERE active;
CREATE INDEX scene_residents ON scene_positions(scene_id) WHERE active;
CREATE TABLE world_arrivals (
 character_id UUID PRIMARY KEY REFERENCES characters(id) ON DELETE CASCADE,
 world_version TEXT NOT NULL, from_q INTEGER NOT NULL, from_r INTEGER NOT NULL,
 to_q INTEGER NOT NULL, to_r INTEGER NOT NULL
);
ALTER TABLE battle_encounters ADD COLUMN scene_id UUID REFERENCES scene_documents(id) ON DELETE SET NULL;
ALTER TABLE loot_containers ADD COLUMN scene_id UUID REFERENCES scene_documents(id) ON DELETE SET NULL;
