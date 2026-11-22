-- Preserve target identity independently of the feedback anchor (carried corpses follow the actor).
alter table production_results add column personal boolean not null default false;
update production_results set personal=true where object_id is null and corpse_id is null;
update production_results r set q=o.q,r=o.r,scene_id=o.scene_id from scene_objects o where r.object_id=o.id;
