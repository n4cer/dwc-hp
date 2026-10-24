# --- !Ups

-- Quake 3 players who played under several nicks; the statistics count all of them as one.
create table quake3_players (
  id                            bigserial not null,
  name                          varchar(255),
  member_id                     bigint,
  constraint pk_quake3_players primary key (id),
  constraint uq_quake3_players_member_id unique (member_id)
);

alter table quake3_players add constraint fk_quake3_players_member_id foreign key (member_id) references lineup (id) on delete set null on update restrict;

create table quake3_player_nicks (
  id                            bigserial not null,
  player_id                     bigint not null,
  nick                          varchar(255) not null,
  constraint pk_quake3_player_nicks primary key (id)
);

alter table quake3_player_nicks add constraint fk_quake3_player_nicks_player_id foreign key (player_id) references quake3_players (id) on delete cascade on update restrict;
create unique index uq_quake3_player_nicks_nick on quake3_player_nicks (lower(nick));

# --- !Downs

drop table if exists quake3_player_nicks cascade;
drop table if exists quake3_players cascade;
