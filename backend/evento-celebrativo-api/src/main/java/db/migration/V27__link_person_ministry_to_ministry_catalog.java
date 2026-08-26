package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;

public class V27__link_person_ministry_to_ministry_catalog extends BaseJavaMigration {

    private static final Map<String, String> NORMALIZED_NAME_BY_LEGACY_TYPE = Map.of(
            "PRIEST", "PRESBITEROS",
            "READER", "LEITORES",
            "COMMENTATOR", "COMENTARISTAS",
            "MINISTER_OF_THE_WORD", "MINISTROS DA PALAVRA",
            "EUCHARISTIC_MINISTER", "MINISTROS DA EUCARISTIA"
    );

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        assertRequiredCatalogRows(connection);
        addMinistryIdColumn(connection);
        backfillMinistryId(connection);
        assertNoUnmappedRows(connection);
        assertNoDuplicatePersonMinistry(connection);
        makeMinistryIdNotNull(connection);
        addMinistryIdIndex(connection);
        addPersonMinistryUniqueConstraint(connection);
        addMinistryForeignKey(connection);
    }

    private void assertRequiredCatalogRows(Connection connection) throws SQLException {
        for (Map.Entry<String, String> entry : NORMALIZED_NAME_BY_LEGACY_TYPE.entrySet()) {
            long count = count(connection, """
                    SELECT COUNT(*)
                    FROM tb_ministry
                    WHERE normalized_name = '%s'
                    """.formatted(entry.getValue()));
            if (count != 1) {
                throw new FlywayException(
                        "V27 nao pode prosseguir: Ministry legado "
                                + entry.getKey()
                                + " esperava exatamente um registro em tb_ministry.normalized_name="
                                + entry.getValue()
                                + ", mas encontrou "
                                + count
                                + "."
                );
            }
        }
    }

    private void addMinistryIdColumn(Connection connection) throws SQLException {
        execute(connection, "ALTER TABLE tb_person_ministry ADD COLUMN ministry_id BIGINT NULL");
    }

    private void backfillMinistryId(Connection connection) throws SQLException {
        execute(connection, """
                UPDATE tb_person_ministry
                SET ministry_id = (
                    SELECT m.id
                    FROM tb_ministry m
                    WHERE m.normalized_name = CASE ministry_type
                        WHEN 'PRIEST' THEN 'PRESBITEROS'
                        WHEN 'READER' THEN 'LEITORES'
                        WHEN 'COMMENTATOR' THEN 'COMENTARISTAS'
                        WHEN 'MINISTER_OF_THE_WORD' THEN 'MINISTROS DA PALAVRA'
                        WHEN 'EUCHARISTIC_MINISTER' THEN 'MINISTROS DA EUCARISTIA'
                    END
                )
                """);
    }

    private void assertNoUnmappedRows(Connection connection) throws SQLException {
        long unmappedRows = count(connection, """
                SELECT COUNT(*)
                FROM tb_person_ministry
                WHERE ministry_id IS NULL
                """);
        if (unmappedRows > 0) {
            throw new FlywayException(
                    "V27 nao pode prosseguir: existem "
                            + unmappedRows
                            + " vinculo(s) em tb_person_ministry sem Ministry persistente correspondente."
            );
        }
    }

    private void assertNoDuplicatePersonMinistry(Connection connection) throws SQLException {
        try (
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("""
                        SELECT person_id, ministry_id
                        FROM tb_person_ministry
                        GROUP BY person_id, ministry_id
                        HAVING COUNT(*) > 1
                        """)
        ) {
            if (resultSet.next()) {
                throw new FlywayException(
                        "V27 nao pode prosseguir: existem vinculos duplicados por person_id e ministry_id."
                );
            }
        }
    }

    private void makeMinistryIdNotNull(Connection connection) throws SQLException {
        String sql = isMySql(connection)
                ? "ALTER TABLE tb_person_ministry MODIFY ministry_id BIGINT NOT NULL"
                : "ALTER TABLE tb_person_ministry ALTER COLUMN ministry_id BIGINT NOT NULL";

        execute(connection, sql);
    }

    private void addMinistryIdIndex(Connection connection) throws SQLException {
        execute(connection, "CREATE INDEX idx_tb_person_ministry_ministry_id ON tb_person_ministry (ministry_id)");
    }

    private void addPersonMinistryUniqueConstraint(Connection connection) throws SQLException {
        execute(connection, """
                ALTER TABLE tb_person_ministry
                    ADD CONSTRAINT uk_tb_person_ministry_person_ministry
                    UNIQUE (person_id, ministry_id)
                """);
    }

    private void addMinistryForeignKey(Connection connection) throws SQLException {
        execute(connection, """
                ALTER TABLE tb_person_ministry
                    ADD CONSTRAINT fk_tb_person_ministry_ministry
                    FOREIGN KEY (ministry_id)
                    REFERENCES tb_ministry(id)
                """);
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
