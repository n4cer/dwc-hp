# --- !Ups

alter table videos add column author_id bigint;
alter table videos add constraint fk_videos_author foreign key (author_id) references lineup (id);
create index ix_videos_author on videos (author_id);

# --- !Downs

drop index if exists ix_videos_author;
alter table videos drop constraint if exists fk_videos_author;
alter table videos drop column if exists author_id;
