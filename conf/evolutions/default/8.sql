# --- !Ups

alter table lineup add column password_hash varchar(255);
alter table lineup add column password_changed_at timestamptz;
alter table lineup add column last_login_at timestamptz;

create table lineup_roles (
  id                            bigserial not null,
  member_id                     bigint not null,
  role                          varchar(32) not null,
  constraint pk_lineup_roles primary key (id),
  constraint uq_lineup_roles_member_role unique (member_id, role)
);

alter table lineup_roles add constraint fk_lineup_roles_member_id foreign key (member_id) references lineup (id) on delete cascade on update restrict;

create table password_tokens (
  id                            bigserial not null,
  member_id                     bigint not null,
  token_hash                    varchar(64) not null,
  expires_at                    timestamptz not null,
  created_by_id                 bigint,
  constraint pk_password_tokens primary key (id),
  constraint uq_password_tokens_member_id unique (member_id),
  constraint uq_password_tokens_token_hash unique (token_hash)
);

alter table password_tokens add constraint fk_password_tokens_member_id foreign key (member_id) references lineup (id) on delete cascade on update restrict;
alter table password_tokens add constraint fk_password_tokens_created_by_id foreign key (created_by_id) references lineup (id) on delete set null on update restrict;

# --- !Downs

drop table if exists password_tokens cascade;
drop table if exists lineup_roles cascade;

alter table lineup drop column if exists last_login_at;
alter table lineup drop column if exists password_changed_at;
alter table lineup drop column if exists password_hash;
