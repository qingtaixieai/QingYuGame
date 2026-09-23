CREATE TABLE battle_encounters (
 id UUID PRIMARY KEY,
 world_version TEXT NOT NULL,
 world_q INTEGER NOT NULL,
 world_r INTEGER NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 round_number INTEGER NOT NULL DEFAULT 1,
 turn_account_id UUID NOT NULL,
 turn_points INTEGER NOT NULL DEFAULT 6,
 attack_used BOOLEAN NOT NULL DEFAULT FALSE,
 turn_deadline BIGINT NOT NULL,
 next_initiative INTEGER NOT NULL DEFAULT 2,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX battle_one_active_per_world_cell
 ON battle_encounters(world_version,world_q,world_r) WHERE active;

CREATE TABLE battle_actors (
 encounter_id UUID NOT NULL REFERENCES battle_encounters(id) ON DELETE CASCADE,
 account_id UUID NOT NULL,
 q INTEGER NOT NULL,
 r INTEGER NOT NULL,
 initiative INTEGER NOT NULL,
 entry_round INTEGER NOT NULL,
 PRIMARY KEY(encounter_id,account_id),
 UNIQUE(encounter_id,q,r),
 UNIQUE(encounter_id,initiative)
);
CREATE UNIQUE INDEX battle_one_active_actor ON battle_actors(account_id);

CREATE TABLE battle_intents (
 encounter_id UUID NOT NULL REFERENCES battle_encounters(id) ON DELETE CASCADE,
 attacker_id UUID NOT NULL,
 target_id UUID NOT NULL,
 q INTEGER NOT NULL,
 r INTEGER NOT NULL,
 visibility VARCHAR(24) NOT NULL DEFAULT 'public',
 PRIMARY KEY(encounter_id,attacker_id)
);

CREATE TABLE battle_events (
 id BIGSERIAL PRIMARY KEY,
 encounter_id UUID NOT NULL REFERENCES battle_encounters(id) ON DELETE CASCADE,
 kind VARCHAR(24) NOT NULL,
 actor_id UUID,
 target_id UUID,
 q INTEGER,
 r INTEGER,
 happened_at BIGINT NOT NULL
);
CREATE INDEX battle_events_recent ON battle_events(encounter_id,id DESC);
