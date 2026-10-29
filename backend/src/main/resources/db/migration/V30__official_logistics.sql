CREATE TABLE logistics_posts(
 id UUID PRIMARY KEY,station_id UUID UNIQUE REFERENCES law_stations(id) ON DELETE CASCADE,
 scene_id UUID NOT NULL REFERENCES scene_documents(id) ON DELETE CASCADE,
 faction_id TEXT NOT NULL REFERENCES factions(id),q INTEGER NOT NULL,r INTEGER NOT NULL,
 capacity INTEGER NOT NULL DEFAULT 2 CHECK(capacity>0),minimum INTEGER NOT NULL DEFAULT 1 CHECK(minimum>=0 AND minimum<capacity),
 allow_external BOOLEAN NOT NULL DEFAULT false,blocked_ms BIGINT NOT NULL DEFAULT 120000 CHECK(blocked_ms>0),
 pickup_ms BIGINT NOT NULL DEFAULT 120000 CHECK(pickup_ms>0),respawn_ms BIGINT NOT NULL DEFAULT 300000 CHECK(respawn_ms>0),
 funding TEXT NOT NULL DEFAULT 'official' CHECK(funding IN ('official','agent')),
 wage_policy TEXT NOT NULL DEFAULT 'fixed_deferred',treasury_copper BIGINT NOT NULL DEFAULT 0 CHECK(treasury_copper>=0)
);
CREATE TABLE logistics_agents(
 actor_id UUID PRIMARY KEY REFERENCES characters(id) ON DELETE CASCADE,
 post_id UUID NOT NULL REFERENCES logistics_posts(id) ON DELETE CASCADE,
 home_q INTEGER NOT NULL,home_r INTEGER NOT NULL,next_think BIGINT NOT NULL DEFAULT 0,
 failures INTEGER NOT NULL DEFAULT 0,task TEXT NOT NULL DEFAULT 'idle'
);
CREATE TABLE logistics_clients(
 actor_id UUID PRIMARY KEY REFERENCES characters(id) ON DELETE CASCADE,
 post_id UUID NOT NULL REFERENCES logistics_posts(id) ON DELETE CASCADE,enabled BOOLEAN NOT NULL DEFAULT true
);
CREATE TABLE logistics_stock_targets(
 post_id UUID NOT NULL REFERENCES logistics_posts(id) ON DELETE CASCADE,code TEXT NOT NULL REFERENCES item_definitions(code),
 quantity INTEGER NOT NULL CHECK(quantity BETWEEN 0 AND 10000),PRIMARY KEY(post_id,code)
);
CREATE TABLE logistics_needs(
 id BIGSERIAL PRIMARY KEY,need_key TEXT NOT NULL,post_id UUID NOT NULL REFERENCES logistics_posts(id) ON DELETE CASCADE,
 kind TEXT NOT NULL CHECK(kind IN ('supply','corpse','stock')),recipient UUID REFERENCES characters(id) ON DELETE CASCADE,
 code TEXT REFERENCES item_definitions(code),corpse_id UUID,
 remaining INTEGER NOT NULL CHECK(remaining>=0),delivered INTEGER NOT NULL DEFAULT 0 CHECK(delivered>=0),
 urgency INTEGER NOT NULL DEFAULT 0,created_at BIGINT NOT NULL,version INTEGER NOT NULL DEFAULT 1,
 state TEXT NOT NULL DEFAULT 'open' CHECK(state IN ('open','claimed','pickup','done','cancelled')),
 carrier UUID REFERENCES characters(id) ON DELETE SET NULL,reserved INTEGER NOT NULL DEFAULT 0 CHECK(reserved>=0 AND reserved<=remaining),
 blocked_since BIGINT NOT NULL DEFAULT 0,retry_after BIGINT NOT NULL DEFAULT 0,
 wait_elapsed BIGINT NOT NULL DEFAULT 0,last_clock BIGINT NOT NULL DEFAULT 0,pickup_active BOOLEAN NOT NULL DEFAULT false,
 message TEXT NOT NULL DEFAULT '',CHECK((kind='corpse' AND corpse_id IS NOT NULL) OR (kind<>'corpse' AND code IS NOT NULL))
);
CREATE UNIQUE INDEX logistics_one_active_need ON logistics_needs(need_key) WHERE state IN ('open','claimed','pickup');
CREATE INDEX logistics_dispatch ON logistics_needs(state,urgency DESC,created_at,id);
CREATE TABLE logistics_lots(
 id BIGSERIAL PRIMARY KEY,owner UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
 code TEXT NOT NULL REFERENCES item_definitions(code),quantity INTEGER NOT NULL CHECK(quantity>=0),
 unit_cost BIGINT NOT NULL CHECK(unit_cost>=0),funding TEXT NOT NULL CHECK(funding IN ('official','agent')),
 fund_post UUID REFERENCES logistics_posts(id) ON DELETE CASCADE,fund_actor UUID REFERENCES characters(id) ON DELETE CASCADE,
 created_at BIGINT NOT NULL,CHECK((funding='official' AND fund_post IS NOT NULL) OR (funding='agent' AND fund_actor IS NOT NULL))
);
CREATE TABLE logistics_events(
 id BIGSERIAL PRIMARY KEY,request_id UUID NOT NULL UNIQUE,need_id BIGINT REFERENCES logistics_needs(id) ON DELETE SET NULL,
 actor UUID REFERENCES characters(id) ON DELETE SET NULL,kind TEXT NOT NULL,code TEXT,quantity INTEGER NOT NULL DEFAULT 0,
 copper BIGINT NOT NULL DEFAULT 0,happened_at BIGINT NOT NULL,details TEXT NOT NULL DEFAULT ''
);
CREATE TABLE logistics_stops(
 requester UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,target UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
 requested_at BIGINT NOT NULL,expires_at BIGINT NOT NULL,accepted BOOLEAN NOT NULL DEFAULT false,
 PRIMARY KEY(requester,target),CHECK(requester<>target)
);
-- Ordinary handovers retain shop/gift prices. Cost quotes are server-generated for actual courier stock transfers.
ALTER TABLE handovers ADD COLUMN cost_quote BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE handovers ADD COLUMN logistics_need BIGINT REFERENCES logistics_needs(id) ON DELETE SET NULL;
ALTER TABLE handovers ADD COLUMN logistics_version INTEGER;
CREATE OR REPLACE FUNCTION reserved_quantity(actor UUID,code TEXT) RETURNS BIGINT LANGUAGE SQL STABLE AS $$
 SELECT coalesce((SELECT quantity FROM inventory_reservations r WHERE r.actor_id=actor AND r.item_code=code),0)
 +coalesce((SELECT sum(l.quantity) FROM handover_lines l JOIN handovers h ON h.id=l.offer_id WHERE h.sender=actor AND l.code=code AND h.state='pending'),0)
 +coalesce((SELECT sum(n.reserved) FROM logistics_needs n WHERE n.carrier=actor AND n.code=code AND n.state IN ('claimed','pickup')),0)
$$;
