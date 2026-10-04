-- Corpses are instances, never inventory stacks. No parent-container column: nesting is impossible.
CREATE TABLE loot_containers (
 id UUID PRIMARY KEY,
 kind TEXT NOT NULL CHECK(kind IN ('corpse','bag')),
 name TEXT NOT NULL,
 source_kind TEXT CHECK(source_kind IN ('monster','player')),
 source_id UUID,
 world_version TEXT NOT NULL, q INTEGER NOT NULL, r INTEGER NOT NULL,
 battle_id UUID REFERENCES battle_encounters(id), battle_q INTEGER, battle_r INTEGER,
 carrier_id UUID REFERENCES accounts(id) ON DELETE CASCADE,
 created_at BIGINT NOT NULL, expires_at BIGINT,
 CHECK ((carrier_id IS NULL AND expires_at IS NOT NULL) OR
        (carrier_id IS NOT NULL AND kind='corpse' AND expires_at IS NULL AND battle_id IS NULL)),
 CHECK ((battle_id IS NULL AND battle_q IS NULL AND battle_r IS NULL) OR
        (battle_id IS NOT NULL AND battle_q IS NOT NULL AND battle_r IS NOT NULL))
);
CREATE INDEX loot_ground ON loot_containers(world_version,q,r) WHERE carrier_id IS NULL;
CREATE INDEX loot_carried ON loot_containers(carrier_id) WHERE carrier_id IS NOT NULL;
CREATE INDEX loot_expiry ON loot_containers(expires_at) WHERE carrier_id IS NULL;
CREATE TABLE loot_items (
 container_id UUID NOT NULL REFERENCES loot_containers(id) ON DELETE CASCADE,
 item_code TEXT NOT NULL REFERENCES item_definitions(code),
 quantity BIGINT NOT NULL CHECK(quantity>0), PRIMARY KEY(container_id,item_code)
);
