-- 统一地基：背包从"绑账号"改为"绑角色"。
-- 玩家 id == 角色 id（characters.account_id 指向 accounts），所以玩家数据不变；
-- 怪（无账号的 character）从此与玩家共用同一张背包表。
-- 之后 creature_items 并入本表并删除。

ALTER TABLE inventories DROP CONSTRAINT inventories_account_id_fkey;
ALTER TABLE inventories RENAME COLUMN account_id TO character_id;
ALTER TABLE inventories ADD CONSTRAINT inventories_character_id_fkey
  FOREIGN KEY (character_id) REFERENCES characters(id) ON DELETE CASCADE;

-- 把生物的私有背包并入统一背包表
INSERT INTO inventories(character_id,item_code,quantity)
  SELECT character_id,item_code,quantity FROM creature_items WHERE quantity > 0
  ON CONFLICT(character_id,item_code) DO UPDATE SET quantity=inventories.quantity+excluded.quantity;

DROP TABLE creature_items;
