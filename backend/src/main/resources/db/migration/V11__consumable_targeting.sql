ALTER TABLE item_definitions ADD COLUMN use_action TEXT;
ALTER TABLE item_definitions ADD COLUMN target_mode TEXT CHECK(target_mode IN ('self','character'));
ALTER TABLE item_definitions ADD COLUMN use_range INTEGER CHECK(use_range>=0);
ALTER TABLE item_definitions ADD COLUMN heal_amount INTEGER CHECK(heal_amount>=0);
UPDATE item_definitions SET use_action='bandage',target_mode='character',use_range=1,heal_amount=4 WHERE code='bandage';
