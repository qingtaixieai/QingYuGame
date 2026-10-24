ALTER TABLE loot_containers DROP CONSTRAINT loot_containers_check1;
ALTER TABLE loot_containers ADD CONSTRAINT loot_local_coordinates CHECK (
 (battle_id IS NULL AND scene_id IS NULL AND battle_q IS NULL AND battle_r IS NULL) OR
 ((battle_id IS NOT NULL OR scene_id IS NOT NULL) AND battle_q IS NOT NULL AND battle_r IS NOT NULL)
);
ALTER TABLE loot_containers DROP CONSTRAINT loot_containers_scene_id_fkey;
ALTER TABLE loot_containers ADD CONSTRAINT loot_containers_scene_id_fkey FOREIGN KEY(scene_id) REFERENCES scene_documents(id) ON DELETE CASCADE;
