package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.StringJoiner;

/**
 * Remove fisicamente tb_event_person: tb_event_assignment ja e a fonte oficial e unica das
 * escalas desde a desativacao do espelho legado. Antes de qualquer DDL, confirma que todo vinculo
 * legado (event_id, person_id) possui pelo menos um assignment oficial correspondente; se houver
 * vinculo sem representacao oficial, a migration falha sem remover a tabela nem tentar inferir o
 * assignment_type ausente, protegendo bancos atualizados que ainda tenham divergencia historica.
 */
public class V7__remove_legacy_event_person_schema extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        validateNoUnmigratedLegacyLinks(connection);

        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE tb_event_person");
        }
    }

    private void validateNoUnmigratedLegacyLinks(Connection connection) throws SQLException {
        String sql = """
                SELECT ep.event_id, ep.person_id
                FROM tb_event_person ep
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM tb_event_assignment ea
                    WHERE ea.event_id = ep.event_id
                      AND ea.person_id = ep.person_id
                )
                ORDER BY ep.event_id, ep.person_id
                """;

        StringJoiner unmigrated = new StringJoiner("; ");
        long unmigratedCount = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                unmigratedCount++;
                if (unmigratedCount <= 20) {
                    unmigrated.add("event_id=" + resultSet.getLong("event_id")
                            + ", person_id=" + resultSet.getLong("person_id"));
                }
            }
        }

        if (unmigratedCount > 0) {
            String sample = unmigratedCount > 20
                    ? unmigrated + "; ... (" + (unmigratedCount - 20) + " vinculos adicionais omitidos)"
                    : unmigrated.toString();
            throw new FlywayException(
                    "Nao foi possivel remover tb_event_person: " + unmigratedCount
                            + " vinculo(s) legado(s) sem assignment oficial correspondente em tb_event_assignment: "
                            + sample
            );
        }
    }
}
