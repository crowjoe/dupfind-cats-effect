CREATE TABLE files (
    file VARCHAR PRIMARY KEY,
    checksum VARCHAR NOT NULL,
    modified TIMESTAMP NOT NULL
);

CREATE INDEX idx_file ON files (file);
