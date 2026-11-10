ALTER TABLE item_definitions ADD COLUMN category TEXT NOT NULL DEFAULT 'material';
ALTER TABLE item_definitions ADD COLUMN subtype TEXT NOT NULL DEFAULT '';
ALTER TABLE item_definitions ADD COLUMN armor_piercing INTEGER NOT NULL DEFAULT 0 CHECK(armor_piercing>=0);
UPDATE item_definitions SET category=kind;
UPDATE item_definitions SET subtype='axe' WHERE code='axe';
INSERT INTO item_definitions(code,name,description,kind,category,subtype,grants_attack) VALUES
 ('fiber','植物纤维','采集纤维植物，用于绷带及制作。','material','material','fiber',false),
 ('iron_ore','铁矿','使用手持镐采矿，熔炼成铁锭。','material','material','ore',false),
 ('iron_ingot','铁锭','两份铁矿熔炼，可制作武器和箭矢。','material','material','ingot',false),
 ('hide','兽皮','对指定毛皮兽尸体剥皮获得，可制作轻甲。','material','material','hide',false);
INSERT INTO item_definitions(code,name,description,kind,category,subtype,hand_usage,grants_attack,damage,armor_piercing,buy_price,sell_price) VALUES
 ('pickaxe','镐子','手持可采矿；相邻单格攻击，基础伤害3，本次忽略1护甲。','weapon','weapon','pickaxe','main_two',true,3,1,100,20),
 ('skinning_knife','剥皮刀','匕首类工具，手持可剥皮；相邻单格攻击，基础伤害2。','weapon','weapon','dagger','main_one',true,2,0,100,20);
CREATE TABLE production_scene_content(scene_id UUID PRIMARY KEY REFERENCES scene_documents(id) ON DELETE CASCADE,version INTEGER NOT NULL);
CREATE TABLE scene_objects(
 id UUID PRIMARY KEY,scene_id UUID NOT NULL REFERENCES scene_documents(id) ON DELETE CASCADE,
 kind TEXT NOT NULL CHECK(kind IN ('resource','facility')),code TEXT NOT NULL,name TEXT NOT NULL,
 q INTEGER NOT NULL,r INTEGER NOT NULL,capacity INTEGER NOT NULL DEFAULT 1 CHECK(capacity>0),
 ready_at BIGINT NOT NULL DEFAULT 0,duration_ms INTEGER NOT NULL DEFAULT 0,respawn_ms INTEGER NOT NULL DEFAULT 0,
 tool TEXT,UNIQUE(scene_id,q,r)
);
CREATE INDEX scene_objects_scene ON scene_objects(scene_id);
ALTER TABLE loot_containers ADD COLUMN remaining_ms BIGINT NOT NULL DEFAULT 1800000 CHECK(remaining_ms>=0);
ALTER TABLE loot_containers ADD COLUMN skinnable BOOLEAN NOT NULL DEFAULT false;
CREATE TABLE creature_harvest(species TEXT PRIMARY KEY,item_code TEXT NOT NULL REFERENCES item_definitions(code),chance INTEGER NOT NULL CHECK(chance BETWEEN 0 AND 100));
INSERT INTO creature_harvest VALUES('claw','hide',80);
UPDATE loot_containers SET skinnable=true WHERE source_kind='monster' AND source_species='claw';
CREATE TABLE production_tasks(
 actor_id UUID PRIMARY KEY REFERENCES characters(id) ON DELETE CASCADE,kind TEXT NOT NULL CHECK(kind IN ('craft','gather','skin')),
 recipe TEXT,object_id UUID REFERENCES scene_objects(id) ON DELETE CASCADE,
 corpse_id UUID UNIQUE REFERENCES loot_containers(id) ON DELETE CASCADE,
 scene_id UUID REFERENCES scene_documents(id) ON DELETE CASCADE,world_version TEXT NOT NULL,
 world_q INTEGER NOT NULL,world_r INTEGER NOT NULL,q INTEGER,r INTEGER,
 started_at BIGINT NOT NULL,due_at BIGINT NOT NULL,total INTEGER NOT NULL CHECK(total>0),completed INTEGER NOT NULL DEFAULT 0,
 CHECK(completed>=0 AND completed<total)
);
CREATE UNIQUE INDEX production_resource_claim ON production_tasks(object_id) WHERE kind='gather';
CREATE TABLE inventory_reservations(
 actor_id UUID NOT NULL REFERENCES production_tasks(actor_id) ON DELETE CASCADE,
 item_code TEXT NOT NULL REFERENCES item_definitions(code),quantity BIGINT NOT NULL CHECK(quantity>0),PRIMARY KEY(actor_id,item_code)
);
CREATE FUNCTION reserved_quantity(actor UUID,code TEXT) RETURNS BIGINT LANGUAGE SQL STABLE AS $$
 SELECT coalesce((SELECT quantity FROM inventory_reservations WHERE actor_id=actor AND item_code=code),0)
$$;
CREATE FUNCTION guard_reserved_inventory() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF (CASE WHEN TG_OP='DELETE' THEN 0 ELSE NEW.quantity END)<reserved_quantity(OLD.character_id,OLD.item_code) THEN
  RAISE EXCEPTION 'Inventory is reserved by an active task' USING ERRCODE='23514';
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
END $$;
CREATE TRIGGER protect_reserved_inventory BEFORE UPDATE OR DELETE ON inventories FOR EACH ROW EXECUTE FUNCTION guard_reserved_inventory();
CREATE TABLE transaction_receipts(
 actor_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,request_id UUID NOT NULL,
 kind TEXT NOT NULL,payload TEXT NOT NULL,result TEXT NOT NULL,created_at BIGINT NOT NULL,
 PRIMARY KEY(actor_id,request_id)
);
CREATE TABLE production_notices(actor_id UUID PRIMARY KEY REFERENCES characters(id) ON DELETE CASCADE,message TEXT NOT NULL,created_at BIGINT NOT NULL);
