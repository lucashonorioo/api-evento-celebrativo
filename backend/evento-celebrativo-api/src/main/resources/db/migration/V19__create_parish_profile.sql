-- Perfil institucional unico da paroquia representada por esta instalacao (uma instalacao = uma
-- paroquia, sem multi-tenancy). tb_parish_profile e uma tabela singleton: id fixo em 1, sem
-- AUTO_INCREMENT, com constraint de CHECK que impede fisicamente qualquer outro valor de id e,
-- combinada com a PRIMARY KEY, impede a existencia de uma segunda linha. A linha id=1 e inserida
-- imediatamente por esta migration (configured=false) para eliminar corrida na primeira
-- configuracao: o service sempre localiza e bloqueia (SELECT ... FOR UPDATE) uma linha ja
-- existente, nunca insere.
CREATE TABLE tb_parish_profile (
    id BIGINT NOT NULL,
    configured BOOLEAN NOT NULL DEFAULT FALSE,
    name VARCHAR(150),
    diocese VARCHAR(150),
    institutional_phone VARCHAR(30),
    institutional_email VARCHAR(254),
    office_address VARCHAR(255),
    office_hours VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_tb_parish_profile PRIMARY KEY (id),
    CONSTRAINT chk_tb_parish_profile_singleton_id CHECK (id = 1),
    CONSTRAINT chk_tb_parish_profile_configured_identity CHECK (
        configured = FALSE OR (name IS NOT NULL AND diocese IS NOT NULL)
    )
);

INSERT INTO tb_parish_profile (id, configured)
VALUES (1, FALSE);
