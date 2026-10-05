-- Per-creature inventory. Monsters are characters (not accounts), and `inventories`
-- is account-scoped, so creatures need their own item table. On death these move
-- into the corpse container.
CREATE TABLE creature_items (
  character_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
  item_code TEXT NOT NULL REFERENCES item_definitions(code),
  quantity BIGINT NOT NULL CHECK(quantity >= 0),
  PRIMARY KEY(character_id, item_code)
);
