CREATE TABLE management_companies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    inn VARCHAR(12),
    address_pattern VARCHAR(255)
);

INSERT INTO management_companies (name, inn, address_pattern) VALUES
    ('УК «Пример-1»', '7701234567', 'Баумана'),
    ('УК «Пример-2»', '7701234568', 'Баумана'),
    ('УК «Центральная»', '7701234569', 'Пушкина');

ALTER TABLE buildings ADD COLUMN uk_id BIGINT REFERENCES management_companies(id);
UPDATE buildings SET uk_id = 1 WHERE id = 1;