package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Suporte a conflitos de escala x indisponibilidade notificados automaticamente
 * (feature/schedule-conflict-notifications): adiciona {@code active_source_key} a
 * tb_notification, usado para garantir no maximo uma ocorrencia ativa (nao resolvida) por
 * conflito (identidade eventId+personId). A UNIQUE permite multiplos NULL (destinatarios de
 * notificacoes que nao sao conflitos de escala nunca preenchem esta coluna) e ja cobre a
 * necessidade de indice dedicado, entao nenhum indice separado e criado para ela. O indice de
 * source_type/source_key ja existe desde a V16 e nao e recriado aqui.
 */
public class V17__support_active_schedule_conflict_notifications extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        execute(connection, "ALTER TABLE tb_notification ADD COLUMN active_source_key VARCHAR(255) NULL");
        execute(connection, """
                ALTER TABLE tb_notification
                    ADD CONSTRAINT uk_tb_notification_active_source_key
                    UNIQUE (active_source_key)
                """);
        execute(connection,
                "CREATE INDEX idx_tb_notification_category_resolved_at ON tb_notification (category, resolved_at)");
        execute(connection,
                "CREATE INDEX idx_tb_notification_reference ON tb_notification (reference_type, reference_id)");
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
