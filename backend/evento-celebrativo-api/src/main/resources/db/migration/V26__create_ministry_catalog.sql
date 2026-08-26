-- Catalogo persistente de ministerios organizacionais. Esta etapa e aditiva: os fluxos atuais
-- continuam usando MinistryType/PersonMinistry temporariamente; tb_ministry existe em paralelo
-- como fundacao para a migracao futura para ministry_id.
CREATE TABLE tb_ministry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    normalized_name VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_tb_ministry PRIMARY KEY (id),
    CONSTRAINT uk_tb_ministry_normalized_name UNIQUE (normalized_name)
);

INSERT INTO tb_ministry (name, normalized_name, active)
VALUES
    ('Presbíteros', 'PRESBITEROS', TRUE),
    ('Leitores', 'LEITORES', TRUE),
    ('Comentaristas', 'COMENTARISTAS', TRUE),
    ('Ministros da Palavra', 'MINISTROS DA PALAVRA', TRUE),
    ('Ministros da Eucaristia', 'MINISTROS DA EUCARISTIA', TRUE);
