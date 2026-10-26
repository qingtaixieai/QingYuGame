CREATE TABLE handover_corpses(
 offer_id UUID NOT NULL REFERENCES handovers(id) ON DELETE CASCADE,
 corpse_id UUID PRIMARY KEY REFERENCES loot_containers(id) ON DELETE CASCADE
);
CREATE FUNCTION protect_handover_corpse() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF EXISTS(SELECT 1 FROM handover_corpses r JOIN handovers h ON h.id=r.offer_id WHERE r.corpse_id=OLD.id AND h.state='pending') THEN
  RAISE EXCEPTION 'Corpse reserved by pending handover' USING ERRCODE='23514';
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;RETURN NEW;
END $$;
CREATE TRIGGER protect_handover_corpse BEFORE UPDATE OR DELETE ON loot_containers FOR EACH ROW EXECUTE FUNCTION protect_handover_corpse();
CREATE FUNCTION clear_handover_corpse_reservations() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.state<>'pending' THEN DELETE FROM handover_corpses WHERE offer_id=NEW.id; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER clear_handover_corpse_reservations AFTER UPDATE ON handovers FOR EACH ROW EXECUTE FUNCTION clear_handover_corpse_reservations();
CREATE FUNCTION default_malicious_creature() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.kind='monster' AND NEW.species IN ('bully','claw','archer') THEN NEW.malicious=true; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER default_malicious_creature BEFORE INSERT ON characters FOR EACH ROW EXECUTE FUNCTION default_malicious_creature();
