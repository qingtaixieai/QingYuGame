ALTER TABLE battle_actors ADD COLUMN withdraw_direction INTEGER;
ALTER TABLE battle_encounters ADD COLUMN turn_start_edge BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE battle_actors ADD CONSTRAINT battle_withdraw_direction CHECK (withdraw_direction BETWEEN 0 AND 5);
