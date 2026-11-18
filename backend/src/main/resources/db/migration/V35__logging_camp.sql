INSERT INTO item_definitions(code,name,description) VALUES
 ('camp_venom','毒藤汁','废弃伐木营的货物。在工作台上可用来给普通箭淬毒。');
UPDATE item_definitions SET description='造成直接伤害后施加中毒。可以在工作台制作。' WHERE code='poison_arrow';

CREATE TABLE scene_logging_camps (
 scene_id UUID PRIMARY KEY REFERENCES scene_documents(id) ON DELETE CASCADE,
 chest_id UUID NOT NULL REFERENCES loot_containers(id) ON DELETE CASCADE,
 monster_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
 restock_at BIGINT,
 patrol_index INTEGER NOT NULL DEFAULT 0
);
