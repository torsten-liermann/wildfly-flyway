-- PostgreSQL-specific migration (should not be executed in H2 tests)
CREATE TABLE POSTGRESQL_SPECIFIC_TABLE (
    id SERIAL PRIMARY KEY,
    database_type VARCHAR(50)
);

INSERT INTO POSTGRESQL_SPECIFIC_TABLE (database_type) VALUES ('PostgreSQL');