# --- Created by Ebean DDL
# To stop Ebean DDL generation, remove this comment and start using Evolutions

# --- !Ups

create table clanwars (
  id                            bigserial not null,
  date                          timestamptz,
  enemy                         varchar(255),
  url                           varchar(255),
  league_id                     bigint,
  report                        TEXT,
  game_id                       bigint,
  gametype_id                   bigint,
  country_id                    bigint,
  constraint pk_clanwars primary key (id)
);

create table countries (
  id                            bigserial not null,
  country                       varchar(255),
  short                         varchar(255),
  flag                          varchar(255),
  constraint pk_countries primary key (id)
);

create table games (
  id                            bigserial not null,
  short                         varchar(255),
  description                   varchar(255),
  constraint pk_games primary key (id)
);

create table gametype (
  id                            bigserial not null,
  game_type                     varchar(255),
  constraint pk_gametype primary key (id)
);

create table history (
  id                            bigserial not null,
  content                       varchar(255),
  timestamp                     timestamptz,
  username_id                   bigint,
  constraint pk_history primary key (id)
);

create table leagues (
  id                            bigserial not null,
  league                        varchar(255),
  constraint pk_leagues primary key (id)
);

create table maps (
  id                            bigserial not null,
  map                           varchar(255),
  game_id                       bigint,
  constraint pk_maps primary key (id)
);

create table match_lineup (
  id                            bigserial not null,
  match_id                      bigint,
  member_id                     bigint,
  constraint pk_match_lineup primary key (id)
);

create table news (
  id                            bigserial not null,
  topic                         varchar(255),
  content                       TEXT,
  timestamp                     timestamptz,
  username_id                   bigint,
  constraint pk_news primary key (id)
);

create table scores (
  id                            bigserial not null,
  match_id                      bigint,
  dwc_score                     integer,
  enemy_score                   integer,
  map_id                        bigint,
  constraint pk_scores primary key (id)
);

create table score_images (
  id                            bigserial not null,
  score_id                      bigint,
  image                         varchar(255),
  constraint pk_score_images primary key (id)
);

create table squads (
  id                            bigserial not null,
  short                         varchar(255),
  description                   varchar(255),
  game                          integer,
  constraint pk_squads primary key (id)
);

create table lineup (
  id                            bigserial not null,
  nick                          varchar(255),
  password                      varchar(255),
  realname                      varchar(255),
  email                         varchar(255),
  birth_date                    timestamptz,
  city                          varchar(255),
  job                           varchar(255),
  quote                         varchar(255),
  since                         timestamptz,
  image                         varchar(255),
  squad                         integer,
  notits                        boolean default false,
  type                          integer,
  active                        boolean default true,
  constraint uq_lineup_nick unique (nick),
  constraint uq_lineup_email unique (email),
  constraint pk_lineup primary key (id)
);

alter table clanwars add constraint fk_clanwars_league_id foreign key (league_id) references leagues (id) on delete restrict on update restrict;
create index ix_clanwars_league_id on clanwars (league_id);

alter table clanwars add constraint fk_clanwars_game_id foreign key (game_id) references games (id) on delete restrict on update restrict;
create index ix_clanwars_game_id on clanwars (game_id);

alter table clanwars add constraint fk_clanwars_gametype_id foreign key (gametype_id) references gametype (id) on delete restrict on update restrict;
create index ix_clanwars_gametype_id on clanwars (gametype_id);

alter table clanwars add constraint fk_clanwars_country_id foreign key (country_id) references countries (id) on delete restrict on update restrict;
create index ix_clanwars_country_id on clanwars (country_id);

alter table history add constraint fk_history_username_id foreign key (username_id) references lineup (id) on delete restrict on update restrict;
create index ix_history_username_id on history (username_id);

alter table maps add constraint fk_maps_game_id foreign key (game_id) references games (id) on delete restrict on update restrict;
create index ix_maps_game_id on maps (game_id);

alter table match_lineup add constraint fk_match_lineup_match_id foreign key (match_id) references clanwars (id) on delete restrict on update restrict;
create index ix_match_lineup_match_id on match_lineup (match_id);

alter table match_lineup add constraint fk_match_lineup_member_id foreign key (member_id) references lineup (id) on delete restrict on update restrict;
create index ix_match_lineup_member_id on match_lineup (member_id);

alter table news add constraint fk_news_username_id foreign key (username_id) references lineup (id) on delete restrict on update restrict;
create index ix_news_username_id on news (username_id);

alter table scores add constraint fk_scores_match_id foreign key (match_id) references clanwars (id) on delete restrict on update restrict;
create index ix_scores_match_id on scores (match_id);

alter table scores add constraint fk_scores_map_id foreign key (map_id) references maps (id) on delete restrict on update restrict;
create index ix_scores_map_id on scores (map_id);

alter table score_images add constraint fk_score_images_score_id foreign key (score_id) references scores (id) on delete restrict on update restrict;
create index ix_score_images_score_id on score_images (score_id);


# --- !Downs

alter table if exists clanwars drop constraint if exists fk_clanwars_league_id;
drop index if exists ix_clanwars_league_id;

alter table if exists clanwars drop constraint if exists fk_clanwars_game_id;
drop index if exists ix_clanwars_game_id;

alter table if exists clanwars drop constraint if exists fk_clanwars_gametype_id;
drop index if exists ix_clanwars_gametype_id;

alter table if exists clanwars drop constraint if exists fk_clanwars_country_id;
drop index if exists ix_clanwars_country_id;

alter table if exists history drop constraint if exists fk_history_username_id;
drop index if exists ix_history_username_id;

alter table if exists maps drop constraint if exists fk_maps_game_id;
drop index if exists ix_maps_game_id;

alter table if exists match_lineup drop constraint if exists fk_match_lineup_match_id;
drop index if exists ix_match_lineup_match_id;

alter table if exists match_lineup drop constraint if exists fk_match_lineup_member_id;
drop index if exists ix_match_lineup_member_id;

alter table if exists news drop constraint if exists fk_news_username_id;
drop index if exists ix_news_username_id;

alter table if exists scores drop constraint if exists fk_scores_match_id;
drop index if exists ix_scores_match_id;

alter table if exists scores drop constraint if exists fk_scores_map_id;
drop index if exists ix_scores_map_id;

alter table if exists score_images drop constraint if exists fk_score_images_score_id;
drop index if exists ix_score_images_score_id;

drop table if exists clanwars cascade;

drop table if exists countries cascade;

drop table if exists games cascade;

drop table if exists gametype cascade;

drop table if exists history cascade;

drop table if exists leagues cascade;

drop table if exists maps cascade;

drop table if exists match_lineup cascade;

drop table if exists news cascade;

drop table if exists scores cascade;

drop table if exists score_images cascade;

drop table if exists squads cascade;

drop table if exists lineup cascade;

