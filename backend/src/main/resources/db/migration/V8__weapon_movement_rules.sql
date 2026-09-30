ALTER TABLE item_definitions ADD COLUMN move_rule TEXT NOT NULL DEFAULT 'range' CHECK(move_rule IN ('range','translated_shape'));
UPDATE item_definitions SET move_rule='translated_shape' WHERE code IN ('spear','axe');
ALTER TABLE battle_intents ADD COLUMN move_rule TEXT NOT NULL DEFAULT 'range';
ALTER TABLE battle_intents ADD COLUMN origin_q INTEGER;
ALTER TABLE battle_intents ADD COLUMN origin_r INTEGER;
-- Existing pending attacks retain their old rule; newly declared attacks snapshot their origin and policy.
