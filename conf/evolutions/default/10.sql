# --- !Ups

-- Quake 3 demos of a map played in a clan war, optional and any number per map.
create table score_demos (
  id                            bigserial not null,
  score_id                      bigint not null,
  file                          varchar(255) not null,
  player                        varchar(255) not null,
  constraint pk_score_demos primary key (id)
);

alter table score_demos add constraint fk_score_demos_score_id foreign key (score_id) references scores (id) on delete cascade on update restrict;
create index ix_score_demos_score_id on score_demos (score_id);

-- n4bot demos already taken over, so that one removed in the administration stays removed.
create table n4bot_demo_imports (
  n4bot_id                      varchar(64) not null,
  clanwar_id                    bigint,
  imported_at                   timestamptz not null,
  constraint pk_n4bot_demo_imports primary key (n4bot_id)
);

# --- !Downs

drop table if exists n4bot_demo_imports cascade;
drop table if exists score_demos cascade;
