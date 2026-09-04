package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public class V25__enforce_person_public_id_constraints extends BaseJavaMigration {

    private static final String TB_PERSON = "tb_person";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        assertNoNullPublicIds(connection);
        assertNoDuplicatePublicIds(connection);

        makePublicIdNotNull(connection);
        addPublicIdUniqueConstraint(connection);
    }

    private void assertNoNullPublicIds(Connection connection) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM tb_person
                WHERE public_id IS NULL
                """;

        try (
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)
        ) {
            resultSet.next();
            long nullCount = resultSet.getLong(1);

            if (nullCount > 0) {
                throw new FlywayException(
                        "V25 não pode prosseguir: existem "
                                + nullCount
                                + " pessoa(s) sem public_id."
                );
            }
        }
    }

    private void assertNoDuplicatePublicIds(Connection connection) throws SQLException {
        String sql = """
                SELECT public_id
                FROM tb_person
                WHERE public_id IS NOT NULL
                GROUP BY public_id
                HAVING COUNT(*) > 1
                """;

        try (
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)
        ) {
            if (resultSet.next()) {
                throw new FlywayException(
                        "V25 não pode prosseguir: existem public_ids duplicados em tb_person."
                );
            }
        }
    }

    private void makePublicIdNotNull(Connection connection) throws SQLException {
        String sql = isMySql(connection)
                ? "ALTER TABLE tb_person MODIFY public_id BINARY(16) NOT NULL"
                : "ALTER TABLE tb_person ALTER COLUMN public_id SET NOT NULL";

        execute(connection, sql);
    }

    private void addPublicIdUniqueConstraint(Connection connection) throws SQLException {
        execute(connection, """
                ALTER TABLE tb_person
                    ADD CONSTRAINT uk_tb_person_public_id
                    UNIQUE (public_id)
                """);
    }

    private boolean isMySql(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName();

        return productName != null
                && productName.toUpperCase(Locale.ROOT).contains("MYSQL");
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
