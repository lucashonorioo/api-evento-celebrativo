-- Lifecycle explicito de CelebrationEvent: substitui "evento existe ou foi apagado" por um estado
-- operacional explicito (ACTIVE ou CANCELLED). Cancelamento nunca exclui fisicamente o evento, sua
-- escala, assignments ou participation responses. Todo evento ja existente migra para ACTIVE, o
-- unico estado operacional que existia implicitamente ate aqui.
ALTER TABLE tb_celebration_event
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE tb_celebration_event
    ADD CONSTRAINT chk_tb_celebration_event_status
    CHECK (status IN ('ACTIVE', 'CANCELLED'));
