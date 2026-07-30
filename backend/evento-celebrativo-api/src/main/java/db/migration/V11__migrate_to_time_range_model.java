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
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Conclui a migracao para intervalos semiabertos [start_at, end_at).
 *
 * <p>Eventos legados recebem start_at pela combinacao exata de event_date e event_time.
 * Como todos os dados anteriores a esta migration sao dados descartaveis de desenvolvimento,
 * end_at recebe exclusivamente no backfill o valor start_at + 1 hora. No modelo definitivo,
 * end_at representa o termino previsto do compromisso para planejamento de disponibilidade e
 * escala, e nao o termino real da celebracao.</p>
 *
 * <p>Indisponibilidades legadas preservam os dias inclusivos do modelo anterior:
 * start_at = start_date 00:00 e end_at = (end_date + 1 dia) 00:00.</p>
 */
public class V11__migrate_to_time_range_model extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String databaseProductName = connection.getMetaData().getDatabaseProductName();
        boolean h2 = isH2(databaseProductName);
        if (!h2 && !isMySql(databaseProductName)) {
            throw new FlywayException("Banco nao suportado pela V11: " + databaseProductName);
        }

        migratePersonUnavailability(connection, h2);
        migrateCelebrationEvent(connection, h2);
    }

    private void migratePersonUnavailability(Connection connection, boolean h2) throws SQLException {
        String dateTimeType = businessDateTimeType(h2);
        addColumnIfMissing(connection, "tb_person_unavailability", "start_at", dateTimeType);
        addColumnIfMissing(connection, "tb_person_unavailability", "end_at", dateTimeType);

        backfillPersonUnavailabilityRange(connection);
        validateNoNullRange(connection, "tb_person_unavailability");

        try (Statement statement = connection.createStatement()) {
            applyNotNull(statement, "tb_person_unavailability", "start_at", dateTimeType, h2);
            applyNotNull(statement, "tb_person_unavailability", "end_at", dateTimeType, h2);

            /*
             * A nova UNIQUE e criada antes da remocao de qualquer indice antigo iniciado por
             * person_id. No MySQL/InnoDB isso garante que a FK continue sustentada durante toda
             * a migration e evita "Cannot drop index ... needed in a foreign key constraint".
             */
            statement.execute(
                    "ALTER TABLE tb_person_unavailability ADD CONSTRAINT "
                            + "uk_tb_person_unavailability_person_range "
                            + "UNIQUE (person_id, start_at, end_at)"
            );

            if (h2) {
                statement.execute(
                        "ALTER TABLE tb_person_unavailability "
                                + "DROP CONSTRAINT uk_tb_person_unavailability_person_dates"
                );
                statement.execute(
                        "ALTER TABLE tb_person_unavailability "
                                + "DROP CONSTRAINT chk_tb_person_unavailability_dates"
                );
                statement.execute("DROP INDEX idx_tb_person_unavailability_person_dates");
                statement.execute("DROP INDEX idx_tb_person_unavailability_dates_person");
            } else {
                statement.execute(
                        "ALTER TABLE tb_person_unavailability "
                                + "DROP CHECK chk_tb_person_unavailability_dates"
                );
                statement.execute(
                        "ALTER TABLE tb_person_unavailability "
                                + "DROP INDEX uk_tb_person_unavailability_person_dates"
                );
                statement.execute(
                        "ALTER TABLE tb_person_unavailability "
                                + "DROP INDEX idx_tb_person_unavailability_person_dates"
                );
                statement.execute(
                        "ALTER TABLE tb_person_unavailability "
                                + "DROP INDEX idx_tb_person_unavailability_dates_person"
                );
            }

            statement.execute("ALTER TABLE tb_person_unavailability DROP COLUMN start_date");
            statement.execute("ALTER TABLE tb_person_unavailability DROP COLUMN end_date");
            statement.execute(
                    "ALTER TABLE tb_person_unavailability ADD CONSTRAINT "
                            + "chk_tb_person_unavailability_range CHECK (start_at < end_at)"
            );
            statement.execute(
                    "CREATE INDEX idx_tb_person_unavailability_person_end_start "
                            + "ON tb_person_unavailability (person_id, end_at, start_at)"
            );
            statement.execute(
                    "CREATE INDEX idx_tb_person_unavailability_range_person "
                            + "ON tb_person_unavailability (start_at, end_at, person_id)"
            );
        }
    }

    private void backfillPersonUnavailabilityRange(Connection connection) throws SQLException {
        record Row(long id, LocalDate startDate, LocalDate endDate) {
        }

        List<Row> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT id, start_date, end_date FROM tb_person_unavailability")) {
            while (resultSet.next()) {
                LocalDate startDate = resultSet.getObject("start_date", LocalDate.class);
                LocalDate endDate = resultSet.getObject("end_date", LocalDate.class);
                if (startDate == null || endDate == null) {
                    throw new FlywayException(
                            "Indisponibilidade legada " + resultSet.getLong("id")
                                    + " nao possui start_date/end_date"
                    );
                }
                rows.add(new Row(resultSet.getLong("id"), startDate, endDate));
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE tb_person_unavailability SET start_at = ?, end_at = ? WHERE id = ?")) {
            for (Row row : rows) {
                statement.setTimestamp(1, Timestamp.valueOf(row.startDate().atStartOfDay()));
                statement.setTimestamp(2, Timestamp.valueOf(row.endDate().plusDays(1).atStartOfDay()));
                statement.setLong(3, row.id());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void migrateCelebrationEvent(Connection connection, boolean h2) throws SQLException {
        String dateTimeType = businessDateTimeType(h2);
        addColumnIfMissing(connection, "tb_celebration_event", "start_at", dateTimeType);
        addColumnIfMissing(connection, "tb_celebration_event", "end_at", dateTimeType);

        backfillCelebrationEventRange(connection);
        validateNoNullRange(connection, "tb_celebration_event");

        try (Statement statement = connection.createStatement()) {
            applyNotNull(statement, "tb_celebration_event", "start_at", dateTimeType, h2);
            applyNotNull(statement, "tb_celebration_event", "end_at", dateTimeType, h2);

            statement.execute(
                    "ALTER TABLE tb_celebration_event ADD CONSTRAINT "
                            + "chk_tb_celebration_event_range CHECK (start_at < end_at)"
            );
            statement.execute(
                    "CREATE INDEX idx_tb_celebration_event_start_end "
                            + "ON tb_celebration_event (start_at, end_at, id)"
            );
            statement.execute(
                    "CREATE INDEX idx_tb_celebration_event_end_start "
                            + "ON tb_celebration_event (end_at, start_at, id)"
            );
            statement.execute("ALTER TABLE tb_celebration_event DROP COLUMN event_date");
            statement.execute("ALTER TABLE tb_celebration_event DROP COLUMN event_time");
        }
    }

    private void backfillCelebrationEventRange(Connection connection) throws SQLException {
        record Row(long id, LocalDate eventDate, LocalTime eventTime) {
        }

        List<Row> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT id, event_date, event_time FROM tb_celebration_event")) {
            while (resultSet.next()) {
                LocalDate eventDate = resultSet.getObject("event_date", LocalDate.class);
                LocalTime eventTime = resultSet.getObject("event_time", LocalTime.class);
                long id = resultSet.getLong("id");
                if (eventDate == null || eventTime == null) {
                    throw new FlywayException(
                            "Evento legado " + id + " nao possui event_date/event_time"
                    );
                }
                if (eventTime.getNano() != 0) {
                    throw new FlywayException(
                            "Evento legado " + id + " possui fracao de segundo nao suportada"
                    );
                }
                rows.add(new Row(id, eventDate, eventTime));
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE tb_celebration_event SET start_at = ?, end_at = ? WHERE id = ?")) {
            for (Row row : rows) {
                LocalDateTime startAt = LocalDateTime.of(row.eventDate(), row.eventTime());
                statement.setTimestamp(1, Timestamp.valueOf(startAt));
                statement.setTimestamp(2, Timestamp.valueOf(startAt.plusHours(1)));
                statement.setLong(3, row.id());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void addColumnIfMissing(
            Connection connection,
            String tableName,
            String columnName,
            String dateTimeType
    ) throws SQLException {
        if (columnExists(connection, tableName, columnName)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " "
                            + dateTimeType + " NULL"
            );
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, null, null)) {
            while (columns.next()) {
                if (tableName.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        && columnName.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void validateNoNullRange(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM " + tableName
                             + " WHERE start_at IS NULL OR end_at IS NULL")) {
            resultSet.next();
            long nullRanges = resultSet.getLong(1);
            if (nullRanges > 0) {
                throw new FlywayException(
                        "A V11 encontrou " + nullRanges + " intervalo(s) nulo(s) em " + tableName
                );
            }
        }
    }

    private void applyNotNull(
            Statement statement,
            String tableName,
            String columnName,
            String dateTimeType,
            boolean h2
    ) throws SQLException {
        if (h2) {
            statement.execute(
                    "ALTER TABLE " + tableName + " ALTER COLUMN " + columnName + " "
                            + dateTimeType + " NOT NULL"
            );
        } else {
            statement.execute(
                    "ALTER TABLE " + tableName + " MODIFY COLUMN " + columnName + " "
                            + dateTimeType + " NOT NULL"
            );
        }
    }

    private String businessDateTimeType(boolean h2) {
        return h2 ? "TIMESTAMP(0)" : "DATETIME(0)";
    }

    private boolean isH2(String databaseProductName) {
        return databaseProductName != null && databaseProductName.toUpperCase().contains("H2");
    }

    private boolean isMySql(String databaseProductName) {
        return databaseProductName != null && databaseProductName.toUpperCase().contains("MYSQL");
    }
}
