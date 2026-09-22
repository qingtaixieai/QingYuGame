CREATE TABLE item_definitions (code TEXT PRIMARY KEY, name TEXT NOT NULL);
INSERT INTO item_definitions VALUES ('wood','木头'),('stone','石头');
CREATE TABLE inventories (
 account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
 item_code TEXT NOT NULL REFERENCES item_definitions(code),
 quantity BIGINT NOT NULL CHECK(quantity >= 0), PRIMARY KEY(account_id,item_code)
);
CREATE TABLE resource_nodes (
 id UUID PRIMARY KEY, kind TEXT NOT NULL CHECK(kind IN ('wood','stone')),
 q INTEGER NOT NULL, r INTEGER NOT NULL, ready_at BIGINT NOT NULL DEFAULT 0,
 UNIQUE(q,r)
);
CREATE TABLE world_actions (
 account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
 kind TEXT NOT NULL, node_id UUID NOT NULL UNIQUE REFERENCES resource_nodes(id) ON DELETE CASCADE,
 q INTEGER NOT NULL, r INTEGER NOT NULL, started_at BIGINT NOT NULL, ends_at BIGINT NOT NULL
);
