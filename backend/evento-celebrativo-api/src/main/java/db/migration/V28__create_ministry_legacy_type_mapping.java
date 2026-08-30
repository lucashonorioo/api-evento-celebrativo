package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

public class V28__create_ministry_legacy_type_mapping extends BaseJavaMigration {

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
        createMappingTable(connection);
        seedLegacyMappings(connection);
        assertExactlyFiveMappings(connection);
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
                        "V28 nao pode prosseguir: Ministry legado "
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

    private void createMappingTable(Connection connection) throws SQLException {
        execute(connection, """
                CREATE TABLE tb_ministry_legacy_type_mapping (
                    ministry_id BIGINT NOT NULL,
                    ministry_type VARCHAR(50) NOT NULL,
                    CONSTRAINT pk_tb_ministry_legacy_type_mapping PRIMARY KEY (ministry_id),
                    CONSTRAINT uk_tb_ministry_legacy_type_mapping_type UNIQUE (ministry_type),
                    CONSTRAINT fk_tb_ministry_legacy_type_mapping_ministry
                        FOREIGN KEY (ministry_id) REFERENCES tb_ministry(id),
                    CONSTRAINT ck_tb_ministry_legacy_type_mapping_type
                        CHECK (ministry_type IN (
                            'PRIEST',
                            'READER',
                            'COMMENTATOR',
                            'MINISTER_OF_THE_WORD',
                            'EUCHARISTIC_MINISTER'
                        ))
                )
                """);
    }

    private void seedLegacyMappings(Connection connection) throws SQLException {
        for (Map.Entry<String, String> entry : NORMALIZED_NAME_BY_LEGACY_TYPE.entrySet()) {
            execute(connection, """
                    INSERT INTO tb_ministry_legacy_type_mapping (ministry_id, ministry_type)
                    SELECT id, '%s'
                    FROM tb_ministry
                    WHERE normalized_name = '%s'
                    """.formatted(entry.getKey(), entry.getValue()));
        }
    }

    private void assertExactlyFiveMappings(Connection connection) throws SQLException {
        long rowCount = count(connection, "SELECT COUNT(*) FROM tb_ministry_legacy_type_mapping");
        long distinctMinistryCount = count(connection, "SELECT COUNT(DISTINCT ministry_id) FROM tb_ministry_legacy_type_mapping");
        long distinctTypeCount = count(connection, "SELECT COUNT(DISTINCT ministry_type) FROM tb_ministry_legacy_type_mapping");

        if (rowCount != 5 || distinctMinistryCount != 5 || distinctTypeCount != 5) {
            throw new FlywayException(
                    "V28 nao pode prosseguir: mapping legado esperava 5 linhas, 5 Ministries distintos e 5 MinistryTypes distintos."
            );
        }

        for (String ministryType : NORMALIZED_NAME_BY_LEGACY_TYPE.keySet()) {
            long count = count(connection, """
                    SELECT COUNT(*)
                    FROM tb_ministry_legacy_type_mapping
                    WHERE ministry_type = '%s'
                    """.formatted(ministryType));
            if (count != 1) {
                throw new FlywayException(
                        "V28 nao pode prosseguir: MinistryType legado "
                                + ministryType
                                + " esperava exatamente um mapping, mas encontrou "
                                + count
                                + "."
                );
            }
        }
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
