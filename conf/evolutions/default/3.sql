# --- !Ups

create table lineup_squads (
  id                            bigserial not null,
  member_id                     bigint,
  squad_id                      bigint,
  constraint pk_lineup_squads primary key (id)
);

alter table lineup_squads add constraint fk_lineup_squads_member_id foreign key (member_id) references lineup (id) on delete restrict on update restrict;
create index ix_lineup_squads_member_id on lineup_squads (member_id);

alter table lineup_squads add constraint fk_lineup_squads_squad_id foreign key (squad_id) references squads (id) on delete restrict on update restrict;
create index ix_lineup_squads_squad_id on lineup_squads (squad_id);

insert into lineup_squads (member_id, squad_id)
  select id, squad from lineup where squad is not null;

alter table lineup drop column password;
alter table lineup drop column type;
alter table lineup drop column notits;
alter table lineup drop column squad;

# --- !Downs

alter table lineup add column squad integer;
alter table lineup add column notits boolean default false;
alter table lineup add column type integer;
alter table lineup add column password varchar(255);

update lineup set squad = (
  select squad_id from lineup_squads where lineup_squads.member_id = lineup.id limit 1
);

alter table if exists lineup_squads drop constraint if exists fk_lineup_squads_member_id;
drop index if exists ix_lineup_squads_member_id;

alter table if exists lineup_squads drop constraint if exists fk_lineup_squads_squad_id;
drop index if exists ix_lineup_squads_squad_id;

drop table if exists lineup_squads cascade;
