ALTER TABLE characters ADD COLUMN selected_ammo TEXT NOT NULL DEFAULT 'arrow' REFERENCES item_definitions(code);
ALTER TABLE characters ADD COLUMN behavior TEXT NOT NULL DEFAULT 'balanced' CHECK(behavior IN ('balanced','aggressive','cautious'));
ALTER TABLE item_definitions ADD COLUMN buy_price INTEGER CHECK(buy_price>=0);
ALTER TABLE item_definitions ADD COLUMN sell_price INTEGER CHECK(sell_price>=0);
ALTER TABLE item_definitions ADD COLUMN ammo_family TEXT;
ALTER TABLE item_definitions ADD COLUMN applied_status TEXT;
ALTER TABLE battle_events ADD COLUMN details TEXT;

CREATE TABLE status_definitions(code TEXT PRIMARY KEY,name TEXT NOT NULL,description TEXT NOT NULL,tick_damage INTEGER NOT NULL CHECK(tick_damage>=0),duration INTEGER NOT NULL CHECK(duration>0));
INSERT INTO status_definitions VALUES('poison','中毒','自身行动开始损失1生命；倒地仍结算，战外暂停。重复施加刷新次数。',1,3);
CREATE TABLE character_statuses(character_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,status_code TEXT NOT NULL REFERENCES status_definitions(code),source_id UUID REFERENCES characters(id) ON DELETE SET NULL,remaining INTEGER NOT NULL CHECK(remaining>0),PRIMARY KEY(character_id,status_code));

INSERT INTO item_definitions(code,name,description,kind,grants_attack) VALUES
('copper_coin','铜币','实际钱币；100铜币等值1银币，背包中不会自动兑换。','currency',false),
('silver_coin','银币','实际钱币；100银币等值1金币。','currency',false),
('gold_coin','金币','实际钱币；1金币等值10000铜币。','currency',false),
('bread','面包','可交易商品，暂时没有使用效果。','material',false),
('poison_arrow','毒箭','实际造成至少1点直接伤害后施加中毒；只能由管理员发放。','ammo',false);
INSERT INTO item_definitions(code,name,description,kind,grants_attack,use_action,target_mode,use_range,attack_cost) VALUES
('antidote','解毒药','清除自身中毒，不恢复生命；战斗内消耗1行动点。','consumable',false,'antidote','self',0,1);
UPDATE item_definitions SET ammo_family='arrow' WHERE code IN ('arrow','poison_arrow');
UPDATE item_definitions SET applied_status='poison' WHERE code='poison_arrow';
UPDATE item_definitions d SET buy_price=p.buy,sell_price=p.sell FROM (VALUES
('bread',3,1),('arrow',5,1),('poison_arrow',NULL::integer,3),('bandage',10,2),('healing_potion',25,5),('antidote',15,3),
('axe',100,20),('spear',125,25),('flail',150,30),('short_bow',150,30),('light_armor',150,30),('wood',NULL::integer,1),('stone',NULL::integer,1)
) p(code,buy,sell) WHERE d.code=p.code;
UPDATE item_definitions SET buy_price=CASE WHEN hand_usage='off_two' THEN 200 ELSE 100 END,sell_price=CASE WHEN hand_usage='off_two' THEN 40 ELSE 20 END WHERE kind='shield';
