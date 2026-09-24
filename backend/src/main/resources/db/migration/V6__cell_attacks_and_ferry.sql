ALTER TABLE battle_intents ALTER COLUMN target_id DROP NOT NULL;
UPDATE battle_intents SET target_id=NULL;

CREATE TABLE ferry_state (
 id INTEGER PRIMARY KEY CHECK(id=1), world_version TEXT NOT NULL,
 wood INTEGER NOT NULL DEFAULT 0 CHECK(wood>=0), stone INTEGER NOT NULL DEFAULT 0 CHECK(stone>=0),
 built BOOLEAN NOT NULL DEFAULT FALSE,
 phase TEXT NOT NULL DEFAULT 'mainland' CHECK(phase IN ('mainland','outbound','island','inbound')),
 route_index INTEGER NOT NULL DEFAULT 0, next_at BIGINT NOT NULL DEFAULT 0,
 saved_at BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE ferry_passengers (
 account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE
);
