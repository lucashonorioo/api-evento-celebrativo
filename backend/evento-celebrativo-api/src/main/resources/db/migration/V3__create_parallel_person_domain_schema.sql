ALTER TABLE tb_person
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE tb_person
    ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

ALTER TABLE tb_person
    ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

CREATE TABLE tb_person_ministry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    person_id BIGINT NOT NULL,
    ministry_type VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_tb_person_ministry PRIMARY KEY (id),
    CONSTRAINT uk_tb_person_ministry_person_type UNIQUE (person_id, ministry_type),
    CONSTRAINT chk_tb_person_ministry_type CHECK (
        ministry_type IN (
            'PRIEST',
            'READER',
            'COMMENTATOR',
            'MINISTER_OF_THE_WORD',
            'EUCHARISTIC_MINISTER'
        )
    )
);

CREATE INDEX idx_tb_person_ministry_person_id ON tb_person_ministry (person_id);

ALTER TABLE tb_person_ministry
    ADD CONSTRAINT fk_tb_person_ministry_person
    FOREIGN KEY (person_id)
    REFERENCES tb_person (id);

CREATE TABLE tb_user_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    person_id BIGINT NOT NULL,
    username VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_tb_user_account PRIMARY KEY (id),
    CONSTRAINT uk_tb_user_account_person_id UNIQUE (person_id),
    CONSTRAINT uk_tb_user_account_username UNIQUE (username)
);

ALTER TABLE tb_user_account
    ADD CONSTRAINT fk_tb_user_account_person
    FOREIGN KEY (person_id)
    REFERENCES tb_person (id);

CREATE TABLE tb_user_account_role (
    user_account_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT pk_tb_user_account_role PRIMARY KEY (user_account_id, role_id)
);

CREATE INDEX idx_tb_user_account_role_role_id ON tb_user_account_role (role_id);

ALTER TABLE tb_user_account_role
    ADD CONSTRAINT fk_tb_user_account_role_user_account
    FOREIGN KEY (user_account_id)
    REFERENCES tb_user_account (id);

ALTER TABLE tb_user_account_role
    ADD CONSTRAINT fk_tb_user_account_role_role
    FOREIGN KEY (role_id)
    REFERENCES tb_role (id);

CREATE TABLE tb_event_assignment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    person_id BIGINT NOT NULL,
    assignment_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_tb_event_assignment PRIMARY KEY (id),
    -- Current rule: one assignment per person in the same event.
    -- This can be revisited if the domain later allows multiple roles in one event.
    CONSTRAINT uk_tb_event_assignment_event_person UNIQUE (event_id, person_id),
    CONSTRAINT chk_tb_event_assignment_type CHECK (
        assignment_type IN (
            'PRIEST',
            'READER',
            'COMMENTATOR',
            'MINISTER_OF_THE_WORD',
            'EUCHARISTIC_MINISTER'
        )
    )
);

CREATE INDEX idx_tb_event_assignment_event_id ON tb_event_assignment (event_id);
CREATE INDEX idx_tb_event_assignment_person_id ON tb_event_assignment (person_id);
CREATE INDEX idx_tb_event_assignment_assignment_type ON tb_event_assignment (assignment_type);

ALTER TABLE tb_event_assignment
    ADD CONSTRAINT fk_tb_event_assignment_event
    FOREIGN KEY (event_id)
    REFERENCES tb_celebration_event (id);

ALTER TABLE tb_event_assignment
    ADD CONSTRAINT fk_tb_event_assignment_person
    FOREIGN KEY (person_id)
    REFERENCES tb_person (id);
