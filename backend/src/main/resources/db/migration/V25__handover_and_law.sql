CREATE TABLE handovers(
 id UUID PRIMARY KEY,sender UUID NOT NULL REFERENCES characters(id),recipient UUID NOT NULL REFERENCES characters(id),
 request_id UUID NOT NULL,payload TEXT NOT NULL,mode TEXT NOT NULL CHECK(mode IN ('gift','sell')),
 state TEXT NOT NULL DEFAULT 'pending' CHECK(state IN ('pending','completed','cancelled')),
 scene_id UUID NOT NULL REFERENCES scene_documents(id) ON DELETE CASCADE,
 battle_id UUID,round_number INTEGER,turn_deadline BIGINT,created_at BIGINT NOT NULL,
 accepted TEXT,result TEXT,UNIQUE(sender,request_id),CHECK(sender<>recipient)
);
CREATE TABLE handover_lines(
 offer_id UUID NOT NULL REFERENCES handovers(id) ON DELETE CASCADE,
 code TEXT NOT NULL REFERENCES item_definitions(code),quantity INTEGER NOT NULL CHECK(quantity BETWEEN 1 AND 10000),
 price BIGINT NOT NULL CHECK(price>=0),PRIMARY KEY(offer_id,code)
);
CREATE UNIQUE INDEX one_pending_handover ON handovers(sender) WHERE state='pending';
CREATE OR REPLACE FUNCTION reserved_quantity(actor UUID,code TEXT) RETURNS BIGINT LANGUAGE SQL STABLE AS $$
 SELECT coalesce((SELECT quantity FROM inventory_reservations r WHERE r.actor_id=actor AND r.item_code=code),0)
 +coalesce((SELECT sum(l.quantity) FROM handover_lines l JOIN handovers h ON h.id=l.offer_id WHERE h.sender=actor AND l.code=code AND h.state='pending'),0)
$$;
ALTER TABLE battle_actors ADD COLUMN handed_over BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE battle_actors ADD COLUMN sprinted BOOLEAN NOT NULL DEFAULT false;
CREATE TABLE factions(id TEXT PRIMARY KEY,name TEXT NOT NULL);
CREATE TABLE jurisdiction(
 world_version TEXT NOT NULL,q INTEGER NOT NULL,r INTEGER NOT NULL,faction_id TEXT NOT NULL REFERENCES factions(id),
 enforced BOOLEAN NOT NULL,PRIMARY KEY(world_version,q,r,faction_id)
);
CREATE TABLE crimes(
 id BIGSERIAL PRIMARY KEY,actor UUID NOT NULL REFERENCES characters(id),faction_id TEXT NOT NULL REFERENCES factions(id),
 reason TEXT NOT NULL,created_at BIGINT NOT NULL,left_at BIGINT,cleared_at BIGINT
);
CREATE INDEX active_crimes ON crimes(actor,faction_id) WHERE cleared_at IS NULL;
CREATE TABLE corpse_bounties(
 corpse_id UUID NOT NULL REFERENCES loot_containers(id) ON DELETE CASCADE,
 faction_id TEXT NOT NULL REFERENCES factions(id),actor UUID NOT NULL REFERENCES characters(id),
 crime_ids BIGINT[] NOT NULL,reward_silver INTEGER NOT NULL DEFAULT 1,PRIMARY KEY(corpse_id,faction_id)
);
CREATE TABLE law_stations(
 id UUID PRIMARY KEY,scene_id UUID NOT NULL REFERENCES scene_documents(id) ON DELETE CASCADE,
 faction_id TEXT NOT NULL REFERENCES factions(id),officer_id UUID NOT NULL REFERENCES characters(id),
 board_q INTEGER NOT NULL,board_r INTEGER NOT NULL,UNIQUE(scene_id,faction_id)
);
ALTER TABLE characters ADD COLUMN malicious BOOLEAN NOT NULL DEFAULT false;
UPDATE characters SET malicious=true WHERE kind='monster';
CREATE TABLE npc_supply_targets(actor UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,code TEXT NOT NULL REFERENCES item_definitions(code),quantity INTEGER NOT NULL CHECK(quantity>=0),PRIMARY KEY(actor,code));
