-- Corpse ownership belongs to characters, including autonomous NPCs.
ALTER TABLE loot_containers DROP CONSTRAINT loot_containers_carrier_id_fkey;
ALTER TABLE loot_containers ADD CONSTRAINT loot_containers_carrier_id_fkey
 FOREIGN KEY(carrier_id) REFERENCES characters(id) ON DELETE CASCADE;
ALTER TABLE action_layouts DROP CONSTRAINT action_layouts_account_id_fkey;
ALTER TABLE action_layouts ADD CONSTRAINT action_layouts_account_id_fkey
 FOREIGN KEY(account_id) REFERENCES characters(id) ON DELETE CASCADE;
