CREATE TABLE tb_event_participation_response (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    person_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    decline_reason VARCHAR(500),
    responded_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_tb_event_participation_response PRIMARY KEY (id),
    CONSTRAINT uk_tb_event_participation_response_event_person UNIQUE (event_id, person_id),
    CONSTRAINT chk_tb_event_participation_response_status CHECK (
        status IN ('CONFIRMED', 'DECLINED')
    )
);

CREATE INDEX idx_tb_event_participation_response_event_id ON tb_event_participation_response (event_id);
CREATE INDEX idx_tb_event_participation_response_person_id ON tb_event_participation_response (person_id);

ALTER TABLE tb_event_participation_response
    ADD CONSTRAINT fk_tb_event_participation_response_event
    FOREIGN KEY (event_id)
    REFERENCES tb_celebration_event (id)
    ON DELETE CASCADE;

ALTER TABLE tb_event_participation_response
    ADD CONSTRAINT fk_tb_event_participation_response_person
    FOREIGN KEY (person_id)
    REFERENCES tb_person (id)
    ON DELETE CASCADE;
