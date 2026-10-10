ALTER TABLE item_definitions ADD COLUMN ammo_code TEXT REFERENCES item_definitions(code);
ALTER TABLE item_definitions ADD COLUMN aim_cost INTEGER NOT NULL DEFAULT 2 CHECK(aim_cost>0);
ALTER TABLE item_definitions ADD COLUMN aim_damage INTEGER NOT NULL DEFAULT 0 CHECK(aim_damage>=0);
ALTER TABLE characters ADD COLUMN body TEXT REFERENCES item_definitions(code);

INSERT INTO item_definitions(code,name,description,kind,grants_attack) VALUES
 ('arrow','箭矢','实际射击时消耗；瞄准不预扣或锁定箭矢。','ammo',false);
INSERT INTO item_definitions(code,name,description,kind,hand_usage,min_range,max_range,attack_cost,damage,ammo_code,aim_damage) VALUES
 ('short_bow','短弓','双手武器；速射射程2至4格。瞄准后，下次行动可向标记区域内的一名角色射击。','weapon','main_two',2,4,2,3,'arrow',5);
INSERT INTO item_definitions(code,name,description,kind,passive_armor,grants_attack) VALUES
 ('light_armor','轻甲','穿戴在躯干，护甲+1，不影响移动。','armor',1,false);
INSERT INTO item_definitions(code,name,description,kind,grants_attack,use_action,target_mode,use_range,heal_amount,attack_cost) VALUES
 ('healing_potion','治疗药水','恢复自身6点生命；战斗中消耗1行动点。满血或倒地时不能使用。','consumable',false,'potion','self',0,6,1);
UPDATE item_definitions SET use_action='throw-stone',target_mode='character',use_range=2,damage=2,attack_cost=2 WHERE code='stone';

CREATE TABLE skill_definitions (
 code TEXT PRIMARY KEY, name TEXT NOT NULL, description TEXT NOT NULL,
 effect TEXT NOT NULL, action_cost INTEGER NOT NULL CHECK(action_cost>=0),
 min_range INTEGER NOT NULL DEFAULT 1, max_range INTEGER NOT NULL DEFAULT 1
);
INSERT INTO skill_definitions VALUES ('interrupt','打断','打断相邻角色的武器预设或瞄准，不造成伤害。','interrupt_intent',2,1,1);
CREATE TABLE character_skills (
 character_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
 skill_code TEXT NOT NULL REFERENCES skill_definitions(code),
 PRIMARY KEY(character_id,skill_code)
);
ALTER TABLE battle_intents ADD COLUMN intent_kind TEXT NOT NULL DEFAULT 'melee' CHECK(intent_kind IN ('melee','aim'));
ALTER TABLE battle_intents ADD COLUMN ready BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE battle_events ADD COLUMN actor_name TEXT;
ALTER TABLE battle_events ADD COLUMN target_name TEXT;
ALTER TABLE battle_events ADD COLUMN amount INTEGER;
ALTER TABLE battle_events ADD COLUMN blocked INTEGER;
CREATE TABLE battle_members (
 encounter_id UUID NOT NULL REFERENCES battle_encounters(id) ON DELETE CASCADE,
 character_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
 PRIMARY KEY(encounter_id,character_id)
);
INSERT INTO battle_members SELECT encounter_id,account_id FROM battle_actors ON CONFLICT DO NOTHING;
CREATE TABLE recent_battle_reports (
 character_id UUID PRIMARY KEY REFERENCES characters(id) ON DELETE CASCADE,
 encounter_id UUID NOT NULL, finished_at BIGINT NOT NULL, document TEXT NOT NULL
);
