CREATE TABLE scene_action_receipts (
 actor_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
 request_id UUID NOT NULL,
 created_at BIGINT NOT NULL,
 PRIMARY KEY(actor_id,request_id)
);
