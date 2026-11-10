-- Saved scene documents are authoritative; never regenerate them from only a seed.
create table scene_documents (
    id uuid primary key,
    world_version varchar(100) not null,
    document text not null
);
create index scene_documents_world on scene_documents(world_version);
create table scene_portals (
    id uuid primary key,
    first_scene uuid not null references scene_documents(id),
    second_scene uuid not null references scene_documents(id),
    document text not null,
    check(first_scene <> second_scene)
);
