ALTER TABLE item_definitions ADD COLUMN hand_usage TEXT NOT NULL DEFAULT 'none' CHECK(hand_usage IN ('none','main_one','main_two','off_one','off_two'));
ALTER TABLE item_definitions ADD COLUMN passive_armor INTEGER NOT NULL DEFAULT 0 CHECK(passive_armor>=0);
ALTER TABLE item_definitions ADD COLUMN guard_reduction INTEGER NOT NULL DEFAULT 0 CHECK(guard_reduction>=0);
ALTER TABLE item_definitions ADD COLUMN guard_min_damage INTEGER NOT NULL DEFAULT 1 CHECK(guard_min_damage>=0);
ALTER TABLE item_definitions ADD COLUMN grants_attack BOOLEAN NOT NULL DEFAULT true;

UPDATE item_definitions SET hand_usage='main_one' WHERE code='axe';
UPDATE item_definitions SET hand_usage='main_two' WHERE code IN ('spear','flail');
UPDATE item_definitions SET grants_attack=true WHERE kind='weapon';

INSERT INTO item_definitions(code,name,description,kind,hand_usage,passive_armor,guard_reduction,guard_min_damage,grants_attack,damage,attack_cost)
VALUES
 ('buckler','单手盾','装备在副手，被动护甲+1；举盾时可用反应点额外减伤2。','shield','off_one',1,2,1,false,0,2),
 ('tower_shield','双手盾','占用双手，没有普通攻击；被动护甲+2；举盾时可用反应点额外减伤3，最低可挡到0。','shield','off_two',2,3,0,false,0,2),
 ('bandage','绷带','战斗中消耗2行动点治疗自己或相邻站立角色4点生命；战斗外治疗同格站立角色。','consumable','none',0,0,1,false,0,2)
ON CONFLICT(code) DO UPDATE SET
 description=excluded.description,
 kind=excluded.kind,
 hand_usage=excluded.hand_usage,
 passive_armor=excluded.passive_armor,
 guard_reduction=excluded.guard_reduction,
 guard_min_damage=excluded.guard_min_damage,
 grants_attack=excluded.grants_attack;

ALTER TABLE characters ADD COLUMN offhand TEXT REFERENCES item_definitions(code);
ALTER TABLE battle_actors ADD COLUMN reaction_points INTEGER NOT NULL DEFAULT 0 CHECK(reaction_points>=0);
ALTER TABLE battle_actors ADD COLUMN shield_raised BOOLEAN NOT NULL DEFAULT false;
