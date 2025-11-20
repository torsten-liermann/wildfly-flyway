-- H2-specific migration
CREATE TABLE H2_SPECIFIC_TABLE (
    id INT PRIMARY KEY,
    database_type VARCHAR(50)
);

INSERT INTO H2_SPECIFIC_TABLE (id, database_type) VALUES (1, 'H2');