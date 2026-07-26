package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Substitui a unicidade de tb_event_assignment de (event_id, person_id) para
 * (event_id, person_id, assignment_type), permitindo que a mesma pessoa exerca mais de uma
 * funcao ministerial no mesmo evento. A sintaxe de DROP de constraint/indice unico diverge entre
 * H2 e MySQL, por isso o banco e detectado via metadata da conexao.
 */
public class V6__allow_multiple_event_assignments_per_person extends BaseJavaMigration {

    private static final String OLD_CONSTRAINT_NAME = "uk_tb_event_assignment_event_person";
    private static final String NEW_CONSTRAINT_NAME = "uk_tb_event_assignment_event_person_type";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String databaseProductName = connection.getMetaData().getDatabaseProductName();

        try (Statement statement = connection.createStatement()) {
            dropOldConstraint(statement, databaseProductName);
            statement.execute(
                    "ALTER TABLE tb_event_assignment ADD CONSTRAINT " + NEW_CONSTRAINT_NAME
                            + " UNIQUE (event_id, person_id, assignment_type)"
            );
        }
    }

    private void dropOldConstraint(Statement statement, String databaseProductName) throws SQLException {
        if (databaseProductName == null) {
            throw new FlywayException("Nao foi possivel detectar o banco para remover a constraint antiga de tb_event_assignment");
        }

        String normalizedProductName = databaseProductName.toUpperCase();
        if (normalizedProductName.contains("H2")) {
            statement.execute("ALTER TABLE tb_event_assignment DROP CONSTRAINT " + OLD_CONSTRAINT_NAME);
        } else if (normalizedProductName.contains("MYSQL")) {
            statement.execute("ALTER TABLE tb_event_assignment DROP INDEX " + OLD_CONSTRAINT_NAME);
        } else {
            throw new FlywayException(
                    "Banco nao suportado pela V6 de tb_event_assignment: " + databaseProductName
            );
        }
    }
}
