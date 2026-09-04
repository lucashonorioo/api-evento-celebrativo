package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public class V29__remove_person_ministry_legacy_type extends BaseJavaMigration {

    private static final String PERSON_MINISTRY_TABLE = "tb_person_ministry";
    private static final String LEGACY_TYPE_COLUMN = "ministry_type";
    private static final String LEGACY_TYPE_CHECK = "chk_tb_person_ministry_type";
    private static final String LEGACY_PERSON_TYPE_UNIQUE = "uk_tb_person_ministry_person_type";
    private static final String CANONICAL_PERSON_MINISTRY_UNIQUE = "uk_tb_person_ministry_person_ministry";
    private static final String CANONICAL_MINISTRY_FK = "fk_tb_person_ministry_ministry";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        assertCanSafelyDropLegacyColumn(connection);
        dropLegacyPersonTypeUnique(connection);
        dropLegacyPersonTypeCheck(connection);
        dropLegacyTypeColumn(connection);
        assertCanonicalShape(connection);
    }

    private void assertCanSafelyDropLegacyColumn(Connection connection) throws SQLException {
        assertColumnExists(connection, PERSON_MINISTRY_TABLE, LEGACY_TYPE_COLUMN);
        assertColumnExists(connection, PERSON_MINISTRY_TABLE, "ministry_id");
        assertTableExists(connection, "tb_ministry_legacy_type_mapping");

        long nullMinistryIds = count(connection, """
                SELECT COUNT(*)
                FROM tb_person_ministry
                WHERE ministry_id IS NULL
                """);
        if (nullMinistryIds > 0) {
            throw new FlywayException(
                    "V29 nao pode prosseguir: existem "
                            + nullMinistryIds
                            + " vinculo(s) em tb_person_ministry sem ministry_id."
            );
        }

        long danglingMinistryIds = count(connection, """
                SELECT COUNT(*)
                FROM tb_person_ministry pm
                LEFT JOIN tb_ministry m ON m.id = pm.ministry_id
                WHERE m.id IS NULL
                """);
        if (danglingMinistryIds > 0) {
            throw new FlywayException(
                    "V29 nao pode prosseguir: existem "
                            + danglingMinistryIds
                            + " vinculo(s) em tb_person_ministry com ministry_id invalido."
            );
        }

        long inconsistentLegacyMappings = count(connection, """
                SELECT COUNT(*)
                FROM tb_person_ministry pm
                LEFT JOIN tb_ministry_legacy_type_mapping lm ON lm.ministry_id = pm.ministry_id
                WHERE lm.ministry_type IS NULL
                   OR lm.ministry_type <> pm.ministry_type
                """);
        if (inconsistentLegacyMappings > 0) {
            throw new FlywayException(
                    "V29 nao pode prosseguir: existem "
                            + inconsistentLegacyMappings
                            + " vinculo(s) em tb_person_ministry com ministry_type inconsistente com ministry_id."
            );
        }

        long duplicatedCanonicalMemberships = count(connection, """
                SELECT COUNT(*)
                FROM (
                    SELECT person_id, ministry_id
                    FROM tb_person_ministry
                    GROUP BY person_id, ministry_id
                    HAVING COUNT(*) > 1
                ) duplicated
                """);
        if (duplicatedCanonicalMemberships > 0) {
            throw new FlywayException(
                    "V29 nao pode prosseguir: existem vinculos duplicados por person_id e ministry_id."
            );
        }
    }

    private void dropLegacyPersonTypeUnique(Connection connection) throws SQLException {
        if (isMySql(connection)) {
            if (indexExists(connection, PERSON_MINISTRY_TABLE, LEGACY_PERSON_TYPE_UNIQUE)) {
                execute(connection, "ALTER TABLE tb_person_ministry DROP INDEX uk_tb_person_ministry_person_type");
            }
            return;
        }

        execute(connection, "ALTER TABLE tb_person_ministry DROP CONSTRAINT IF EXISTS uk_tb_person_ministry_person_type");
    }

    private void dropLegacyPersonTypeCheck(Connection connection) throws SQLException {
        if (isMySql(connection)) {
            if (constraintExists(connection, PERSON_MINISTRY_TABLE, LEGACY_TYPE_CHECK)) {
                execute(connection, "ALTER TABLE tb_person_ministry DROP CHECK chk_tb_person_ministry_type");
            }
            return;
        }

        execute(connection, "ALTER TABLE tb_person_ministry DROP CONSTRAINT IF EXISTS chk_tb_person_ministry_type");
    }

    private void dropLegacyTypeColumn(Connection connection) throws SQLException {
        if (columnExists(connection, PERSON_MINISTRY_TABLE, LEGACY_TYPE_COLUMN)) {
            execute(connection, "ALTER TABLE tb_person_ministry DROP COLUMN ministry_type");
        }
    }

    private void assertCanonicalShape(Connection connection) throws SQLException {
        assertColumnDoesNotExist(connection, PERSON_MINISTRY_TABLE, LEGACY_TYPE_COLUMN);
        assertColumnExists(connection, PERSON_MINISTRY_TABLE, "ministry_id");

        if (isMySql(connection)) {
            if (!indexExists(connection, PERSON_MINISTRY_TABLE, CANONICAL_PERSON_MINISTRY_UNIQUE)) {
                throw new FlywayException(
                        "V29 nao pode prosseguir: UNIQUE(person_id, ministry_id) canonico nao encontrado."
                );
            }
            if (!constraintExists(connection, PERSON_MINISTRY_TABLE, CANONICAL_MINISTRY_FK)) {
                throw new FlywayException(
                        "V29 nao pode prosseguir: FK canonica para tb_ministry nao encontrada."
                );
            }
            return;
        }

        if (!constraintExists(connection, PERSON_MINISTRY_TABLE, CANONICAL_PERSON_MINISTRY_UNIQUE)) {
            throw new FlywayException(
                    "V29 nao pode prosseguir: UNIQUE(person_id, ministry_id) canonico nao encontrado."
            );
        }
        if (!constraintExists(connection, PERSON_MINISTRY_TABLE, CANONICAL_MINISTRY_FK)) {
            throw new FlywayException(
                    "V29 nao pode prosseguir: FK canonica para tb_ministry nao encontrada."
            );
        }
    }

    private void assertTableExists(Connection connection, String tableName) throws SQLException {
        if (!tableExists(connection, tableName)) {
            throw new FlywayException("V29 nao pode prosseguir: tabela obrigatoria nao encontrada: " + tableName);
        }
    }

    private void assertColumnExists(Connection connection, String tableName, String columnName) throws SQLException {
        if (!columnExists(connection, tableName, columnName)) {
            throw new FlywayException(
                    "V29 nao pode prosseguir: coluna obrigatoria nao encontrada: "
                            + tableName
                            + "."
                            + columnName
            );
        }
    }

    private void assertColumnDoesNotExist(Connection connection, String tableName, String columnName) throws SQLException {
        if (columnExists(connection, tableName, columnName)) {
            throw new FlywayException(
                    "V29 nao concluiu: coluna legada ainda existe: "
                            + tableName
                            + "."
                            + columnName
            );
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getTables(connection.getCatalog(), null, tableName, null)) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metaData.getTables(connection.getCatalog(), null, tableName.toUpperCase(Locale.ROOT), null)) {
            return resultSet.next();
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), null, tableName, columnName)) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metaData.getColumns(
                connection.getCatalog(),
                null,
                tableName.toUpperCase(Locale.ROOT),
                columnName.toUpperCase(Locale.ROOT))) {
            return resultSet.next();
        }
    }

    private boolean constraintExists(Connection connection, String tableName, String constraintName) throws SQLException {
        if (isMySql(connection)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT COUNT(*)
                    FROM information_schema.table_constraints
                    WHERE table_schema = DATABASE()
                      AND table_name = ?
                      AND constraint_name = ?
                    """)) {
                statement.setString(1, tableName);
                statement.setString(2, constraintName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getLong(1) > 0;
                }
            }
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE LOWER(table_name) = LOWER(?)
                  AND LOWER(constraint_name) = LOWER(?)
                """)) {
            statement.setString(1, tableName);
            statement.setString(2, constraintName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1) > 0;
            }
        }
    }

    private boolean indexExists(Connection connection, String tableName, String indexName) throws SQLException {
        if (isMySql(connection)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT COUNT(*)
                    FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = ?
                      AND index_name = ?
                    """)) {
                statement.setString(1, tableName);
                statement.setString(2, indexName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getLong(1) > 0;
                }
            }
        }

        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getIndexInfo(
                connection.getCatalog(),
                connection.getSchema(),
                tableName,
                false,
                false
        )) {
            while (resultSet.next()) {
                String candidate = resultSet.getString("INDEX_NAME");
                if (candidate != null && candidate.equalsIgnoreCase(indexName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isMySql(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName();

        return productName != null
                && productName.toUpperCase(Locale.ROOT).contains("MYSQL");
    }

    private long count(Connection connection, String sql) throws SQLException {
        try (
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)
        ) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
