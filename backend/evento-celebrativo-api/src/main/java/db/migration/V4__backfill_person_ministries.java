package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

public class V4__backfill_person_ministries extends BaseJavaMigration {

    private static final Map<String, String> MINISTRY_BY_PERSON_TYPE = new LinkedHashMap<>();

    static {
        MINISTRY_BY_PERSON_TYPE.put("reader", "READER");
        MINISTRY_BY_PERSON_TYPE.put("commentator", "COMMENTATOR");
        MINISTRY_BY_PERSON_TYPE.put("priest", "PRIEST");
        MINISTRY_BY_PERSON_TYPE.put("minister_of_the_word", "MINISTER_OF_THE_WORD");
        MINISTRY_BY_PERSON_TYPE.put("eucharistic_minister", "EUCHARISTIC_MINISTER");
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        validatePersonTypes(connection);
        for (Map.Entry<String, String> entry : MINISTRY_BY_PERSON_TYPE.entrySet()) {
            insertMissingMinistries(connection, entry.getKey(), entry.getValue());
            reactivateExistingMinistries(connection, entry.getKey(), entry.getValue());
        }
    }

    private void validatePersonTypes(Connection connection) throws SQLException {
        String sql = """
                SELECT DISTINCT person_type
                FROM tb_person
                WHERE person_type IS NULL
                   OR TRIM(person_type) = ''
                   OR person_type NOT IN (?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (String personType : MINISTRY_BY_PERSON_TYPE.keySet()) {
                statement.setString(index++, personType);
            }

            StringJoiner invalidTypes = new StringJoiner(", ");
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String personType = resultSet.getString("person_type");
                    invalidTypes.add(personType == null ? "<null>" : "'" + personType + "'");
                }
            }

            if (invalidTypes.length() > 0) {
                throw new FlywayException("Valores de person_type invalidos em tb_person: " + invalidTypes);
            }
        }
    }

    private void insertMissingMinistries(Connection connection, String personType, String ministryType) throws SQLException {
        String sql = """
                INSERT INTO tb_person_ministry (person_id, ministry_type, active, created_at, updated_at)
                SELECT p.id, ?, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                FROM tb_person p
                WHERE p.person_type = ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM tb_person_ministry pm
                      WHERE pm.person_id = p.id
                        AND pm.ministry_type = ?
                  )
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ministryType);
            statement.setString(2, personType);
            statement.setString(3, ministryType);
            statement.executeUpdate();
        }
    }

    private void reactivateExistingMinistries(Connection connection, String personType, String ministryType) throws SQLException {
        String sql = """
                UPDATE tb_person_ministry
                SET active = TRUE,
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE ministry_type = ?
                  AND active = FALSE
                  AND person_id IN (
                      SELECT id
                      FROM tb_person
                      WHERE person_type = ?
                  )
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ministryType);
            statement.setString(2, personType);
            statement.executeUpdate();
        }
    }
}
