INSERT INTO skill_definitions VALUES
 ('fireball','火球术','吟唱后可在六格内召下一颗陨石，基础伤害5。','fireball',2,0,6);
CREATE TABLE skill_preparations (
 character_id UUID NOT NULL,
 skill_code TEXT NOT NULL,
 encounter_id UUID REFERENCES battle_encounters(id) ON DELETE CASCADE,
 ready_at BIGINT,
 ready BOOLEAN NOT NULL DEFAULT false,
 PRIMARY KEY(character_id,skill_code),
 FOREIGN KEY(character_id,skill_code) REFERENCES character_skills(character_id,skill_code) ON DELETE CASCADE,
 CHECK ((ready AND encounter_id IS NULL AND ready_at IS NULL)
     OR (NOT ready AND ((encounter_id IS NOT NULL) <> (ready_at IS NOT NULL))))
);

CREATE FUNCTION clear_skill_preparation_on_incapacitation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.life IN ('down','dead','soul') AND OLD.life IS DISTINCT FROM NEW.life THEN
  DELETE FROM skill_preparations WHERE character_id=NEW.id;
 END IF;
 RETURN NEW;
END
$$;
CREATE TRIGGER character_incapacitation_clears_preparation
 AFTER UPDATE OF life ON characters FOR EACH ROW EXECUTE FUNCTION clear_skill_preparation_on_incapacitation();
