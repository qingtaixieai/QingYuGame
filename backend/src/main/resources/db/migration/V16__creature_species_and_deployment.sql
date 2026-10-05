-- 怪物种类与投放标记：
--   species  —— 'bully'（恶霸：红身、使斧）或 'claw'（利爪怪：绿身、用爪）
--   deployed —— 是否由管理员手动投放。自动生成的那只恶霸为 false（会被重生），
--               管理员投放的怪一律为 true（死后不重生，只留尸体）。
-- 两者都只是角色的属性，人物/生物共用同一套逻辑。

ALTER TABLE characters ADD COLUMN species VARCHAR(16);
ALTER TABLE characters ADD COLUMN deployed BOOLEAN NOT NULL DEFAULT FALSE;

-- 现有那只自动生成的怪物，统一归为恶霸。
UPDATE characters SET species = 'bully' WHERE kind = 'monster' AND species IS NULL;
