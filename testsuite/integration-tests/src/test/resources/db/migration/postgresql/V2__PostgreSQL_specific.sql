CREATE TABLE pg_specific (
    id SERIAL PRIMARY KEY,
    pg_feature TEXT
);

INSERT INTO pg_specific (pg_feature) VALUES ('PostgreSQL arrays and JSON support');