-- H2-specific migration
-- This migration only runs when using H2 database

CREATE TABLE h2_specific (
    id INTEGER AUTO_INCREMENT PRIMARY KEY,
    h2_feature VARCHAR(100),
    json_data JSON,
    array_data ARRAY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- H2 supports JSON data type
INSERT INTO h2_specific (h2_feature, json_data, array_data) 
VALUES ('H2 in-memory database', '{"name": "H2 Test", "version": 2}', ARRAY['h2', 'test', 'array']);

-- Create a view specific to H2
CREATE VIEW h2_migration_summary AS
SELECT 
    version, 
    description, 
    script,
    FORMATDATETIME(installed_on, 'yyyy-MM-dd HH:mm:ss') as formatted_date
FROM flyway_schema_history
WHERE success = true;