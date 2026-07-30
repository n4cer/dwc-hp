# --- !Ups

alter table lineup add column founder boolean default false;
alter table lineup add column clan_leader boolean default false;
alter table lineup add column honorary_member boolean default false;

# --- !Downs

alter table lineup drop column founder;
alter table lineup drop column clan_leader;
alter table lineup drop column honorary_member;
