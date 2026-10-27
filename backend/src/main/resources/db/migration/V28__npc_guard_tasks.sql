CREATE TABLE guard_posts (
 id UUID PRIMARY KEY REFERENCES law_stations(id) ON DELETE CASCADE,
 scene_id UUID NOT NULL REFERENCES scene_documents(id) ON DELETE CASCADE,
 faction_id TEXT NOT NULL REFERENCES factions(id),q INT NOT NULL,r INT NOT NULL,
 capacity INT NOT NULL DEFAULT 3 CHECK(capacity>0),
 minimum INT NOT NULL DEFAULT 1 CHECK(minimum>=0 AND minimum<capacity),
 batch_size INT NOT NULL DEFAULT 1 CHECK(batch_size>0),
 respawn_ms BIGINT NOT NULL DEFAULT 300000 CHECK(respawn_ms>0)
);
CREATE TABLE npc_tasks (
 actor_id UUID PRIMARY KEY REFERENCES characters(id) ON DELETE CASCADE,
 post_id UUID REFERENCES guard_posts(id) ON DELETE CASCADE,
 home_scene UUID REFERENCES scene_documents(id) ON DELETE CASCADE,
 home_q INT NOT NULL,home_r INT NOT NULL,
 task TEXT NOT NULL DEFAULT 'idle',target_id UUID REFERENCES characters(id) ON DELETE SET NULL,
 last_scene UUID REFERENCES scene_documents(id) ON DELETE SET NULL,last_q INT,last_r INT,
 last_seen BIGINT NOT NULL DEFAULT 0,next_think BIGINT NOT NULL DEFAULT 0,
 failures INT NOT NULL DEFAULT 0,signature TEXT NOT NULL DEFAULT '',
 caution DOUBLE PRECISION NOT NULL DEFAULT 1,duty DOUBLE PRECISION NOT NULL DEFAULT 1,
 damaged_hp INT NOT NULL DEFAULT 0,alarm_id BIGINT,
 corpse_id UUID REFERENCES loot_containers(id) ON DELETE SET NULL
);
CREATE TABLE guard_alarms (
 id BIGSERIAL PRIMARY KEY,faction_id TEXT NOT NULL REFERENCES factions(id),
 target_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
 created_at BIGINT NOT NULL,active BOOLEAN NOT NULL DEFAULT TRUE,
 batch_pending BOOLEAN NOT NULL DEFAULT FALSE,needs_support BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX guard_alarm_active ON guard_alarms(faction_id,target_id) WHERE active;
CREATE INDEX npc_tasks_due ON npc_tasks(next_think);
CREATE INDEX guard_alarms_fifo ON guard_alarms(created_at,id) WHERE active;
