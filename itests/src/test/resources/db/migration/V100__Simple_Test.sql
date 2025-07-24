-- Simple Flyway Test Migration
CREATE TABLE SIMPLE_TEST (
    id INTEGER NOT NULL,
    name VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

INSERT INTO SIMPLE_TEST (id, name) VALUES (1, 'Test Data');
INSERT INTO SIMPLE_TEST (id, name) VALUES (2, 'More Test Data');