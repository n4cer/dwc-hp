# --- !Ups

-- Evolution 2 imports historic rows with explicit IDs. PostgreSQL sequences are
-- not advanced by explicit values, so synchronize every generated primary key.
SELECT setval(pg_get_serial_sequence('clanwars', 'id'), COALESCE(MAX(id), 1), MAX(id) IS NOT NULL) FROM clanwars;
SELECT setval(pg_get_serial_sequence('countries', 'id'), COALESCE(MAX(id), 1), MAX(id) IS NOT NULL) FROM countries;
SELECT setval(pg_get_serial_sequence('games', 'id'), COALESCE(MAX(id), 1), MAX(id) IS NOT NULL) FROM games;
SELECT setval(pg_get_serial_sequence('gametype', 'id'), COALESCE(MAX(id), 1), MAX(id) IS NOT NULL) FROM gametype;
SELECT setval(pg_get_serial_sequence('history', 'id'), COALESCE(MAX(id), 1), MAX(id) IS NOT NULL) FROM history;
SELECT setval(pg_get_serial_sequence('leagues', 'id'), COALESCE(MAX(id), 1), MAX(id) IS NOT NULL) FROM leagues;
SELECT setval(pg_get_serial_sequence('maps', 'id'), COALESCE(MAX(id), 1), MAX(id) IS NOT NULL) FROM maps;
SELECT setval(pg_get_serial_sequence('match_lineup', 'id'), COALESCE(MAX(id), 1), MAX(id) IS NOT NULL) FROM match_lineup;
SELECT setval(pg_get_serial_sequence('news', 'id'), COALESCE(MAX(id), 1), MAX(id) IS NOT NULL) FROM news;
SELECT setval(pg_get_serial_sequence('scores', 'id'), COALESCE(MAX(id), 1), MAX(id) IS NOT NULL) FROM scores;
SELECT setval(pg_get_serial_sequence('score_images', 'id'), COALESCE(MAX(id), 1), MAX(id) IS NOT NULL) FROM score_images;
SELECT setval(pg_get_serial_sequence('squads', 'id'), COALESCE(MAX(id), 1), MAX(id) IS NOT NULL) FROM squads;
SELECT setval(pg_get_serial_sequence('lineup', 'id'), COALESCE(MAX(id), 1), MAX(id) IS NOT NULL) FROM lineup;


# --- !Downs

-- Sequence synchronization is intentionally not reverted because lowering a
-- sequence could cause duplicate primary keys on the next insert.
