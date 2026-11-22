create table chest_views (
 container_id uuid primary key references loot_containers(id) on delete cascade,
 viewer_id uuid not null unique references characters(id) on delete cascade,
 token uuid not null unique,
 opened_at bigint not null
);
create table production_results (
 id uuid primary key, actor_id uuid not null references characters(id) on delete cascade,
 kind text not null, outcome text not null check(outcome in ('success','failure','cancelled')),
 object_id uuid, corpse_id uuid, scene_id uuid, world_version text not null,
 world_q integer not null, world_r integer not null, q integer, r integer,
 happened_at bigint not null, message text not null, items text not null default '[]'
);
create index production_results_actor_time on production_results(actor_id,happened_at desc,id);
