-- Directions 0-5 are world edges. Indoor doors are indexed from 6 by the battle service.
ALTER TABLE battle_actors
    DROP CONSTRAINT battle_withdraw_direction,
    ADD CONSTRAINT battle_withdraw_direction CHECK (withdraw_direction >= 0);
