-- Responsabilidade de coordenacao ministerial: pertence ao vinculo PersonMinistry (ministryType +
-- coordinator), nao a Person/UserAccount/UserAccountRole/ParishStaffAssignment/MinistryType. Um
-- ministerio pode ter 0..N coordenadores; uma Person pode coordenar 0..N ministerios distintos -
-- a UNIQUE(person_id, ministry_type) ja existente continua sendo a garantia estrutural correta,
-- sem necessidade de indice novo.
ALTER TABLE tb_person_ministry
    ADD COLUMN coordinator BOOLEAN NOT NULL DEFAULT FALSE;

-- Invariante estrutural: nunca coordinator=true com active=false. Desativar o ministerio sempre
-- limpa a coordenacao (ver PersonMinistry.deactivate()); esta CHECK e a garantia de banco contra
-- qualquer escrita que burle a entidade.
ALTER TABLE tb_person_ministry
    ADD CONSTRAINT chk_tb_person_ministry_coordinator_requires_active
    CHECK (coordinator = FALSE OR active = TRUE);
