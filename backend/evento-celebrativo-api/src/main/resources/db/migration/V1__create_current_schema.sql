CREATE TABLE tb_celebration_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_date DATE,
    event_time TIME(6),
    mass_or_celebration BOOLEAN,
    name_mass_or_event VARCHAR(255),
    CONSTRAINT pk_tb_celebration_event PRIMARY KEY (id)
);

CREATE TABLE tb_location (
    id BIGINT NOT NULL AUTO_INCREMENT,
    address VARCHAR(255),
    church_name VARCHAR(255),
    CONSTRAINT pk_tb_location PRIMARY KEY (id)
);

CREATE TABLE tb_person (
    id BIGINT NOT NULL AUTO_INCREMENT,
    birthday_date DATE,
    person_type VARCHAR(31) NOT NULL,
    name VARCHAR(255),
    password VARCHAR(255),
    phone_number VARCHAR(255),
    CONSTRAINT pk_tb_person PRIMARY KEY (id),
    CONSTRAINT uk_tb_person_phone_number UNIQUE (phone_number)
);

CREATE TABLE tb_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    authority VARCHAR(255),
    CONSTRAINT pk_tb_role PRIMARY KEY (id)
);

CREATE TABLE tb_event_location (
    event_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL
);

CREATE INDEX idx_tb_event_location_event_id ON tb_event_location (event_id);
CREATE INDEX idx_tb_event_location_location_id ON tb_event_location (location_id);

ALTER TABLE tb_event_location
    ADD CONSTRAINT fk_tb_event_location_event
    FOREIGN KEY (event_id)
    REFERENCES tb_celebration_event (id);

ALTER TABLE tb_event_location
    ADD CONSTRAINT fk_tb_event_location_location
    FOREIGN KEY (location_id)
    REFERENCES tb_location (id);

CREATE TABLE tb_event_person (
    event_id BIGINT NOT NULL,
    person_id BIGINT NOT NULL
);

CREATE INDEX idx_tb_event_person_event_id ON tb_event_person (event_id);
CREATE INDEX idx_tb_event_person_person_id ON tb_event_person (person_id);

ALTER TABLE tb_event_person
    ADD CONSTRAINT fk_tb_event_person_event
    FOREIGN KEY (event_id)
    REFERENCES tb_celebration_event (id);

ALTER TABLE tb_event_person
    ADD CONSTRAINT fk_tb_event_person_person
    FOREIGN KEY (person_id)
    REFERENCES tb_person (id);

CREATE TABLE tb_person_role (
    person_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT pk_tb_person_role PRIMARY KEY (person_id, role_id)
);

CREATE INDEX idx_tb_person_role_role_id ON tb_person_role (role_id);

ALTER TABLE tb_person_role
    ADD CONSTRAINT fk_tb_person_role_person
    FOREIGN KEY (person_id)
    REFERENCES tb_person (id);

ALTER TABLE tb_person_role
    ADD CONSTRAINT fk_tb_person_role_role
    FOREIGN KEY (role_id)
    REFERENCES tb_role (id);
