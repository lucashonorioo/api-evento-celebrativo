package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class V5__backfill_event_assignments extends BaseJavaMigration {

    private static final Map<String, String> ASSIGNMENT_BY_PERSON_TYPE = new LinkedHashMap<>();

    static {
        ASSIGNMENT_BY_PERSON_TYPE.put("priest", "PRIEST");
        ASSIGNMENT_BY_PERSON_TYPE.put("reader", "READER");
        ASSIGNMENT_BY_PERSON_TYPE.put("commentator", "COMMENTATOR");
        ASSIGNMENT_BY_PERSON_TYPE.put("minister_of_the_word", "MINISTER_OF_THE_WORD");
        ASSIGNMENT_BY_PERSON_TYPE.put("eucharistic_minister", "EUCHARISTIC_MINISTER");
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        validateLegacyEventPersonDuplicates(connection);
        Map<EventPersonKey, LegacyEventPerson> legacyEventPeople = loadAndValidateLegacyEventPeople(connection);
        validateExistingAssignmentDuplicates(connection);
        Map<EventPersonKey, ExistingAssignment> existingAssignments = loadExistingAssignments(connection);
        validateNoExtraAssignments(legacyEventPeople, existingAssignments);

        List<LegacyEventPerson> missingAssignments = new ArrayList<>();
        List<AssignmentTypeUpdate> assignmentTypeUpdates = new ArrayList<>();

        for (LegacyEventPerson legacyEventPerson : legacyEventPeople.values()) {
            ExistingAssignment existingAssignment = existingAssignments.get(legacyEventPerson.key());
            if (existingAssignment == null) {
                missingAssignments.add(legacyEventPerson);
            } else if (!legacyEventPerson.expectedAssignmentType().equals(existingAssignment.assignmentType())) {
                assignmentTypeUpdates.add(new AssignmentTypeUpdate(
                        existingAssignment.id(),
                        legacyEventPerson.expectedAssignmentType()
                ));
            }
        }

        insertMissingAssignments(connection, missingAssignments);
        updateIncorrectAssignmentTypes(connection, assignmentTypeUpdates);
    }

    private void validateLegacyEventPersonDuplicates(Connection connection) throws SQLException {
        String sql = """
                SELECT event_id, person_id, COUNT(*) AS row_count
                FROM tb_event_person
                GROUP BY event_id, person_id
                HAVING COUNT(*) > 1
                """;

        StringJoiner duplicates = new StringJoiner("; ");
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                duplicates.add("event_id=" + resultSet.getLong("event_id")
                        + ", person_id=" + resultSet.getLong("person_id")
                        + ", count=" + resultSet.getLong("row_count"));
            }
        }

        if (duplicates.length() > 0) {
            throw new FlywayException("Vinculos duplicados em tb_event_person impedem backfill de tb_event_assignment: "
                    + duplicates);
        }
    }

    private Map<EventPersonKey, LegacyEventPerson> loadAndValidateLegacyEventPeople(Connection connection) throws SQLException {
        String sql = """
                SELECT ep.event_id, ep.person_id, p.person_type
                FROM tb_event_person ep
                INNER JOIN tb_person p ON p.id = ep.person_id
                ORDER BY ep.event_id, ep.person_id
                """;

        Map<EventPersonKey, LegacyEventPerson> legacyEventPeople = new LinkedHashMap<>();
        StringJoiner invalidTypes = new StringJoiner("; ");
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                long eventId = resultSet.getLong("event_id");
                long personId = resultSet.getLong("person_id");
                String personType = resultSet.getString("person_type");
                String expectedAssignmentType = resolveAssignmentType(personType);

                if (expectedAssignmentType == null) {
                    invalidTypes.add("event_id=" + eventId
                            + ", person_id=" + personId
                            + ", person_type=" + formatValue(personType));
                    continue;
                }

                EventPersonKey key = new EventPersonKey(eventId, personId);
                legacyEventPeople.put(key, new LegacyEventPerson(key, expectedAssignmentType));
            }
        }

        if (invalidTypes.length() > 0) {
            throw new FlywayException("person_type invalido em vinculos de evento: " + invalidTypes);
        }

        return legacyEventPeople;
    }

    private void validateExistingAssignmentDuplicates(Connection connection) throws SQLException {
        String sql = """
                SELECT event_id, person_id, COUNT(*) AS row_count
                FROM tb_event_assignment
                GROUP BY event_id, person_id
                HAVING COUNT(*) > 1
                """;

        StringJoiner duplicates = new StringJoiner("; ");
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                duplicates.add("event_id=" + resultSet.getLong("event_id")
                        + ", person_id=" + resultSet.getLong("person_id")
                        + ", count=" + resultSet.getLong("row_count"));
            }
        }

        if (duplicates.length() > 0) {
            throw new FlywayException("Assignments duplicados em tb_event_assignment: " + duplicates);
        }
    }

    private Map<EventPersonKey, ExistingAssignment> loadExistingAssignments(Connection connection) throws SQLException {
        String sql = """
                SELECT id, event_id, person_id, assignment_type
                FROM tb_event_assignment
                ORDER BY event_id, person_id, id
                """;

        Map<EventPersonKey, ExistingAssignment> existingAssignments = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                EventPersonKey key = new EventPersonKey(
                        resultSet.getLong("event_id"),
                        resultSet.getLong("person_id")
                );
                existingAssignments.put(key, new ExistingAssignment(
                        resultSet.getLong("id"),
                        key,
                        resultSet.getString("assignment_type")
                ));
            }
        }
        return existingAssignments;
    }

    private void validateNoExtraAssignments(
            Map<EventPersonKey, LegacyEventPerson> legacyEventPeople,
            Map<EventPersonKey, ExistingAssignment> existingAssignments
    ) {
        StringJoiner extras = new StringJoiner("; ");
        for (ExistingAssignment existingAssignment : existingAssignments.values()) {
            if (!legacyEventPeople.containsKey(existingAssignment.key())) {
                extras.add("assignment_id=" + existingAssignment.id()
                        + ", event_id=" + existingAssignment.key().eventId()
                        + ", person_id=" + existingAssignment.key().personId());
            }
        }

        if (extras.length() > 0) {
            throw new FlywayException("Assignments sem vinculo correspondente em tb_event_person: " + extras);
        }
    }

    private void insertMissingAssignments(Connection connection, List<LegacyEventPerson> missingAssignments) throws SQLException {
        if (missingAssignments.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO tb_event_assignment(event_id, person_id, assignment_type, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (LegacyEventPerson missingAssignment : missingAssignments) {
                statement.setLong(1, missingAssignment.key().eventId());
                statement.setLong(2, missingAssignment.key().personId());
                statement.setString(3, missingAssignment.expectedAssignmentType());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void updateIncorrectAssignmentTypes(Connection connection, List<AssignmentTypeUpdate> assignmentTypeUpdates)
            throws SQLException {
        if (assignmentTypeUpdates.isEmpty()) {
            return;
        }

        String sql = """
                UPDATE tb_event_assignment
                SET assignment_type = ?,
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (AssignmentTypeUpdate assignmentTypeUpdate : assignmentTypeUpdates) {
                statement.setString(1, assignmentTypeUpdate.assignmentType());
                statement.setLong(2, assignmentTypeUpdate.assignmentId());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private String resolveAssignmentType(String personType) {
        if (personType == null || personType.trim().isEmpty()) {
            return null;
        }
        return ASSIGNMENT_BY_PERSON_TYPE.get(personType);
    }

    private String formatValue(String value) {
        if (value == null) {
            return "<null>";
        }
        if (value.isEmpty()) {
            return "<empty>";
        }
        return "'" + value + "'";
    }

    private record EventPersonKey(long eventId, long personId) {
    }

    private record LegacyEventPerson(EventPersonKey key, String expectedAssignmentType) {
    }

    private record ExistingAssignment(long id, EventPersonKey key, String assignmentType) {
    }

    private record AssignmentTypeUpdate(long assignmentId, String assignmentType) {
    }
}
