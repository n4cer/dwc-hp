# --- !Ups

create table downloads (
  id                            bigserial not null,
  title                         varchar(255) not null,
  description                   TEXT,
  category                      varchar(255),
  file                          varchar(255) not null,
  sort_order                    integer default 0,
  published                     boolean default false,
  constraint pk_downloads primary key (id)
);

create index ix_downloads_sort_order on downloads (sort_order, id);

# --- !Downs

drop index if exists ix_downloads_sort_order;
drop table if exists downloads;
