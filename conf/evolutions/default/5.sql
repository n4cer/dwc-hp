# --- !Ups

create table videos (
  id                            bigserial not null,
  title                         varchar(255) not null,
  description                   TEXT,
  release_year                  integer,
  file                          varchar(255) not null,
  poster                        varchar(255),
  original_file                 varchar(255),
  duration_seconds              integer,
  sort_order                    integer default 0,
  published                     boolean default false,
  constraint pk_videos primary key (id)
);

create index ix_videos_sort_order on videos (sort_order, id);

# --- !Downs

drop index if exists ix_videos_sort_order;
drop table if exists videos;
