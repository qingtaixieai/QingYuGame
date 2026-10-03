CREATE TABLE action_layouts (
 account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
 revision BIGINT NOT NULL DEFAULT 0,
 document TEXT NOT NULL
);
