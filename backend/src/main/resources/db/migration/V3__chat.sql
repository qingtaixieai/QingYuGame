ALTER TABLE accounts ADD COLUMN last_login_ip TEXT;
ALTER TABLE accounts ADD COLUMN last_login_at TIMESTAMPTZ;
ALTER TABLE accounts ADD COLUMN last_chat_at BIGINT NOT NULL DEFAULT 0;
DELETE FROM sessions WHERE token_hash IN (SELECT token_hash FROM (SELECT token_hash,row_number() OVER(PARTITION BY account_id ORDER BY expires_at DESC,token_hash) AS rn FROM sessions) s WHERE rn>1);
CREATE UNIQUE INDEX sessions_one_browser ON sessions(account_id);
CREATE TABLE moderation_bans (
 id UUID PRIMARY KEY, target_type TEXT NOT NULL CHECK(target_type IN ('account','ip')),
 target TEXT NOT NULL, scope TEXT NOT NULL CHECK(scope IN ('chat','game')),
 reason VARCHAR(200) NOT NULL DEFAULT '', created_by UUID NOT NULL REFERENCES accounts(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, expires_at TIMESTAMPTZ, revoked_at TIMESTAMPTZ
);
CREATE INDEX moderation_lookup ON moderation_bans(target_type,target,scope) WHERE revoked_at IS NULL;
CREATE TABLE chat_images (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
 status TEXT NOT NULL CHECK(status IN ('active','pending','retired','rejected')),
 mime TEXT NOT NULL, data BYTEA, size INTEGER NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 reviewed_by UUID REFERENCES accounts(id), reviewed_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX chat_image_active ON chat_images(owner_id) WHERE status='active';
CREATE UNIQUE INDEX chat_image_pending ON chat_images(owner_id) WHERE status='pending';
CREATE TABLE chat_messages (
 id BIGSERIAL PRIMARY KEY, sender_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
 recipient_id UUID REFERENCES accounts(id) ON DELETE CASCADE,
 kind TEXT NOT NULL CHECK(kind IN ('text','emote','image')), body VARCHAR(1000) NOT NULL DEFAULT '',
 image_id UUID REFERENCES chat_images(id), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(recipient_id IS NULL OR recipient_id<>sender_id)
);
CREATE INDEX chat_public_history ON chat_messages(id DESC) WHERE recipient_id IS NULL;
CREATE INDEX chat_private_inbox ON chat_messages(recipient_id,sender_id,id DESC);
CREATE INDEX chat_private_outbox ON chat_messages(sender_id,recipient_id,id DESC);
CREATE TABLE chat_reads (
 account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
 conversation TEXT NOT NULL, last_id BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(account_id,conversation)
);
