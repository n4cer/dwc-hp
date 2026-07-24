# --- !Ups

alter table lineup add column exit_date timestamptz default null;

# --- !Downs

alter table lineup drop column exit_date;
