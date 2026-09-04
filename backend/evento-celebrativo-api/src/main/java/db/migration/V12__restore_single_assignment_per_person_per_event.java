package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reverte a V6: restaura a unicidade de tb_event_assignment para (event_id, person_id),
 * encerrando a possibilidade de a mesma pessoa exercer mais de uma funcao ministerial no
 * mesmo evento. Antes de remover a constraint atual, valida que nao existam duplicidades
 * reais de (event_id, person_id); se existirem, a migration falha com diagnostico explicito
 * em vez de escolher automaticamente uma funcao ou apagar dados. A sintaxe de DROP de
 * constraint/indice unico diverge entre H2, MySQL e PostgreSQL, por isso o banco e detectado via
 * metadata da conexao (mesmo mecanismo da V6).
 */
public class V12__restore_single_assignment_per_person_per_event extends BaseJavaMigration {

    private static final String OLD_CONSTRAINT_NAME = "uk_tb_event_assignment_event_person_type";
    private static final String NEW_CONSTRAINT_NAME = "uk_tb_event_assignment_event_person";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        failIfDuplicateAssignmentsExist(connection);

        String databaseProductName = connection.getMetaData().getDatabaseProductName();
        try (Statement statement = connection.createStatement()) {
            dropOldConstraint(statement, databaseProductName);
            statement.execute(
                    "ALTER TABLE tb_event_assignment ADD CONSTRAINT " + NEW_CONSTRAINT_NAME
                            + " UNIQUE (event_id, person_id)"
            );
        }
    }

    private void failIfDuplicateAssignmentsExist(Connection connection) throws SQLException {
        String duplicateKeysQuery = """
                SELECT event_id, person_id, COUNT(*) AS assignment_count
                FROM tb_event_assignment
                GROUP BY event_id, person_id
                HAVING COUNT(*) > 1
                """;
        List<long[]> duplicateKeys = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(duplicateKeysQuery);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            while (resultSet.next()) {
                duplicateKeys.add(new long[] {
                        resultSet.getLong("event_id"),
                        resultSet.getLong("person_id"),
                        resultSet.getLong("assignment_count")
                });
            }
        }
        if (duplicateKeys.isEmpty()) {
            return;
        }

        List<String> diagnostics = new ArrayList<>();
        String typesQuery = """
                SELECT assignment_type
                FROM tb_event_assignment
                WHERE event_id = ? AND person_id = ?
                """;
        try (PreparedStatement typesStatement = connection.prepareStatement(typesQuery)) {
            for (long[] key : duplicateKeys) {
                typesStatement.setLong(1, key[0]);
                typesStatement.setLong(2, key[1]);
                List<String> assignmentTypes = new ArrayList<>();
                try (ResultSet typesResultSet = typesStatement.executeQuery()) {
                    while (typesResultSet.next()) {
                        assignmentTypes.add(typesResultSet.getString("assignment_type"));
                    }
                }
                // Ordenacao feita em Java (nao via SQL) para produzir diagnostico deterministico
                // de forma identica em H2 e MySQL, sem depender de funcoes de agregacao textual
                // especificas de dialeto (ex.: GROUP_CONCAT).
                Collections.sort(assignmentTypes);
                diagnostics.add(
                        "event_id=" + key[0]
                                + ", person_id=" + key[1]
                                + ", assignment_count=" + key[2]
                                + ", assignment_types=" + assignmentTypes
                );
            }
        }

        throw new FlywayException(
                "Nao e possivel aplicar a V12: existem pessoas com mais de uma funcao no mesmo evento em "
                        + "tb_event_assignment. Resolva manualmente cada duplicidade antes de migrar "
                        + "(nao ha resolucao automatica nem exclusao de dados): " + String.join("; ", diagnostics)
        );
    }

    private void dropOldConstraint(Statement statement, String databaseProductName) throws SQLException {
        if (databaseProductName == null) {
            throw new FlywayException("Nao foi possivel detectar o banco para remover a constraint antiga de tb_event_assignment");
        }

        String normalizedProductName = databaseProductName.toUpperCase();
        if (normalizedProductName.contains("H2") || normalizedProductName.contains("POSTGRESQL")) {
            statement.execute("ALTER TABLE tb_event_assignment DROP CONSTRAINT " + OLD_CONSTRAINT_NAME);
        } else if (normalizedProductName.contains("MYSQL")) {
            statement.execute("ALTER TABLE tb_event_assignment DROP INDEX " + OLD_CONSTRAINT_NAME);
        } else {
            throw new FlywayException(
                    "Banco nao suportado pela V12 de tb_event_assignment: " + databaseProductName
            );
        }
    }
}
