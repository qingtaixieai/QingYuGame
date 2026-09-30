ALTER TABLE item_definitions ADD COLUMN description TEXT NOT NULL DEFAULT '';
ALTER TABLE item_definitions ADD COLUMN kind TEXT NOT NULL DEFAULT 'material';
ALTER TABLE item_definitions ADD COLUMN shape TEXT NOT NULL DEFAULT 'single';
ALTER TABLE item_definitions ADD COLUMN min_range INTEGER NOT NULL DEFAULT 1;
ALTER TABLE item_definitions ADD COLUMN max_range INTEGER NOT NULL DEFAULT 1;
ALTER TABLE item_definitions ADD COLUMN attack_cost INTEGER NOT NULL DEFAULT 2 CHECK(attack_cost>0);
ALTER TABLE item_definitions ADD COLUMN damage INTEGER NOT NULL DEFAULT 0 CHECK(damage>=0);
UPDATE item_definitions SET description=CASE code WHEN 'wood' THEN '砍伐获得的木材，可用于公共建设。' ELSE '捡拾获得的石料，可用于公共建设。' END;
INSERT INTO item_definitions(code,name,description,kind,shape,min_range,max_range,attack_cost,damage) VALUES
 ('flail','流星锤','只能攻击第二环的一个格子，无法攻击贴身目标。','weapon','single',2,2,2,5),
 ('axe','斧头','攻击身边两个相连的相邻格，形成扇面。','weapon','fan',1,1,2,3),
 ('spear','长枪','沿一个六边方向贯穿第一格和第二格。','weapon','line',1,2,2,3);
CREATE TABLE characters (
 id UUID PRIMARY KEY, account_id UUID UNIQUE REFERENCES accounts(id) ON DELETE CASCADE,
 kind TEXT NOT NULL DEFAULT 'player', name TEXT NOT NULL DEFAULT '',
 q INTEGER NOT NULL DEFAULT 0,r INTEGER NOT NULL DEFAULT 0,
 strength INTEGER NOT NULL DEFAULT 10, agility INTEGER NOT NULL DEFAULT 10, constitution INTEGER NOT NULL DEFAULT 10,
 intellect INTEGER NOT NULL DEFAULT 10, perception INTEGER NOT NULL DEFAULT 10, willpower INTEGER NOT NULL DEFAULT 10,
 hp INTEGER NOT NULL DEFAULT 20 CHECK(hp>=0), max_hp INTEGER NOT NULL DEFAULT 20,
 down_hp INTEGER NOT NULL DEFAULT 10 CHECK(down_hp>=0), max_down_hp INTEGER NOT NULL DEFAULT 10,
 life TEXT NOT NULL DEFAULT 'alive' CHECK(life IN ('alive','down','soul','dead')),
 weapon TEXT REFERENCES item_definitions(code), bind_q INTEGER NOT NULL,bind_r INTEGER NOT NULL,
 death_q INTEGER,death_r INTEGER, timer_kind TEXT, timer_end BIGINT NOT NULL DEFAULT 0,
 protected_until BIGINT NOT NULL DEFAULT 0, next_move BIGINT NOT NULL DEFAULT 0,
 saved_at BIGINT NOT NULL DEFAULT 0
);
ALTER TABLE battle_actors ADD COLUMN initiative_roll INTEGER NOT NULL DEFAULT 0;
ALTER TABLE battle_actors ADD COLUMN initiative_score INTEGER NOT NULL DEFAULT 0;
ALTER TABLE battle_intents ADD COLUMN cells TEXT;
ALTER TABLE battle_intents ADD COLUMN weapon TEXT NOT NULL DEFAULT 'unarmed';
ALTER TABLE battle_intents ADD COLUMN damage INTEGER NOT NULL DEFAULT 2;
ALTER TABLE battle_intents ADD COLUMN min_range INTEGER NOT NULL DEFAULT 1;
ALTER TABLE battle_intents ADD COLUMN max_range INTEGER NOT NULL DEFAULT 1;
