package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Migra o modelo temporal de dia inteiro (PersonUnavailability.start_date/end_date) e de
 * data+hora separados (CelebrationEvent.event_date/event_time) para um unico intervalo
 * semiaberto [start_at, end_at) em cada tabela.
 *
 * PersonUnavailability: o backfill e exato e reversivel em significado
 * (start_at = start_date as 00:00; end_at = end_date + 1 dia as 00:00), nao ha linhas
 * legadas no repositorio (tabela criada na V10) e por isso a migracao e de fase unica:
 * termina com as colunas antigas removidas e NOT NULL/CHECK/UNIQUE definitivos.
 *
 * CelebrationEvent: start_at e sempre calculavel (event_date + event_time) e recebe backfill
 * exato e NOT NULL nesta mesma V11. end_at NAO pode ser inferido para eventos existentes sem
 * inventar duracao (nenhuma fonte no repositorio contem essa informacao) - por isso a migracao
 * de CelebrationEvent fica em duas fases: V11 apenas adiciona start_at/end_at (end_at nullable),
 * relaxa event_date/event_time para nullable (a aplicacao para de escrever neles) e preserva as
 * colunas antigas fisicamente. A V12, que remove as colunas antigas e fecha end_at como
 * NOT NULL/CHECK definitivo, so devera ser criada quando o end_at de cada evento existente for
 * definido explicitamente (fora do escopo desta migracao).
 */
public class V11__migrate_to_time_range_model extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String databaseProductName = connection.getMetaData().getDatabaseProductName();
        boolean isH2 = isH2(databaseProductName);
        if (!isH2 && !isMySql(databaseProductName)) {
            throw new FlywayException("Banco nao suportado pela V11: " + databaseProductName);
        }

        migratePersonUnavailability(connection, isH2);
        migrateCelebrationEvent(connection, isH2);
    }

    // ---------------------------------------------------------------------
    // PersonUnavailability: fase unica (backfill exato, sem linhas legadas)
    // ---------------------------------------------------------------------

    private void migratePersonUnavailability(Connection connection, boolean isH2) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE tb_person_unavailability ADD COLUMN start_at TIMESTAMP(6) NULL");
            statement.execute("ALTER TABLE tb_person_unavailability ADD COLUMN end_at TIMESTAMP(6) NULL");
        }

        backfillPersonUnavailabilityRange(connection);

        try (Statement statement = connection.createStatement()) {
            if (isH2) {
                statement.execute("ALTER TABLE tb_person_unavailability ALTER COLUMN start_at SET NOT NULL");
                statement.execute("ALTER TABLE tb_person_unavailability ALTER COLUMN end_at SET NOT NULL");
            } else {
                statement.execute("ALTER TABLE tb_person_unavailability MODIFY COLUMN start_at TIMESTAMP(6) NOT NULL");
                statement.execute("ALTER TABLE tb_person_unavailability MODIFY COLUMN end_at TIMESTAMP(6) NOT NULL");
            }

            // A UNIQUE(person_id, start_at, end_at) e criada ANTES de remover os indices/constraints
            // antigos que tambem comecam por person_id: no MySQL/InnoDB, fk_tb_person_unavailability_person
            // exige que sempre exista pelo menos um indice com person_id como coluna mais a esquerda
            // apoiando a FK. Removendo os indices antigos sem um substituto ja existente, o MySQL rejeita
            // o DROP INDEX com "needed in a foreign key constraint". H2 nao tem essa restricao, mas a
            // mesma ordem e segura e correta em ambos os bancos.
            statement.execute(
                    "ALTER TABLE tb_person_unavailability ADD CONSTRAINT uk_tb_person_unavailability_person_range"
                            + " UNIQUE (person_id, start_at, end_at)"
            );

            if (isH2) {
                statement.execute("ALTER TABLE tb_person_unavailability DROP CONSTRAINT uk_tb_person_unavailability_person_dates");
                statement.execute("ALTER TABLE tb_person_unavailability DROP CONSTRAINT chk_tb_person_unavailability_dates");
                statement.execute("DROP INDEX idx_tb_person_unavailability_person_dates");
                statement.execute("DROP INDEX idx_tb_person_unavailability_dates_person");
            } else {
                statement.execute("ALTER TABLE tb_person_unavailability DROP CHECK chk_tb_person_unavailability_dates");
                statement.execute("ALTER TABLE tb_person_unavailability DROP INDEX uk_tb_person_unavailability_person_dates");
                statement.execute("ALTER TABLE tb_person_unavailability DROP INDEX idx_tb_person_unavailability_person_dates");
                statement.execute("ALTER TABLE tb_person_unavailability DROP INDEX idx_tb_person_unavailability_dates_person");
            }

            statement.execute("ALTER TABLE tb_person_unavailability DROP COLUMN start_date");
            statement.execute("ALTER TABLE tb_person_unavailability DROP COLUMN end_date");

            statement.execute(
                    "ALTER TABLE tb_person_unavailability ADD CONSTRAINT chk_tb_person_unavailability_range"
                            + " CHECK (start_at < end_at)"
            );
            statement.execute(
                    "CREATE INDEX idx_tb_person_unavailability_person_range"
                            + " ON tb_person_unavailability (person_id, start_at, end_at)"
            );
            statement.execute(
                    "CREATE INDEX idx_tb_person_unavailability_range_person"
                            + " ON tb_person_unavailability (start_at, end_at, person_id)"
            );
        }
    }

    private void backfillPersonUnavailabilityRange(Connection connection) throws SQLException {
        record Row(long id, LocalDate startDate, LocalDate endDate) {
        }
        java.util.List<Row> rows = new java.util.ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT id, start_date, end_date FROM tb_person_unavailability")) {
            while (resultSet.next()) {
                Date startDate = resultSet.getDate("start_date");
                Date endDate = resultSet.getDate("end_date");
                rows.add(new Row(resultSet.getLong("id"), startDate.toLocalDate(), endDate.toLocalDate()));
            }
        }

        if (rows.isEmpty()) {
            return;
        }

        String sql = "UPDATE tb_person_unavailability SET start_at = ?, end_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Row row : rows) {
                LocalDateTime startAt = row.startDate().atStartOfDay();
                LocalDateTime endAt = row.endDate().plusDays(1).atStartOfDay();
                statement.setTimestamp(1, Timestamp.valueOf(startAt));
                statement.setTimestamp(2, Timestamp.valueOf(endAt));
                statement.setLong(3, row.id());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    // ---------------------------------------------------------------------
    // CelebrationEvent: fase 1 (aditiva) - start_at exato, end_at NAO inferido
    // ---------------------------------------------------------------------

    private void migrateCelebrationEvent(Connection connection, boolean isH2) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE tb_celebration_event ADD COLUMN start_at TIMESTAMP(6) NULL");
            statement.execute("ALTER TABLE tb_celebration_event ADD COLUMN end_at TIMESTAMP(6) NULL");
        }

        backfillCelebrationEventStartAt(connection);

        try (Statement statement = connection.createStatement()) {
            if (isH2) {
                statement.execute("ALTER TABLE tb_celebration_event ALTER COLUMN start_at SET NOT NULL");
                statement.execute("ALTER TABLE tb_celebration_event ALTER COLUMN event_date SET NULL");
                statement.execute("ALTER TABLE tb_celebration_event ALTER COLUMN event_time SET NULL");
            } else {
                statement.execute("ALTER TABLE tb_celebration_event MODIFY COLUMN start_at TIMESTAMP(6) NOT NULL");
                statement.execute("ALTER TABLE tb_celebration_event MODIFY COLUMN event_date DATE NULL");
                statement.execute("ALTER TABLE tb_celebration_event MODIFY COLUMN event_time TIME(6) NULL");
            }

            // CHECK permissiva: linhas legadas com end_at ainda nulo (fase 1) nao violam a
            // constraint (NULL na comparacao nao reprova CHECK); a V12 fechara para NOT NULL.
            statement.execute(
                    "ALTER TABLE tb_celebration_event ADD CONSTRAINT chk_tb_celebration_event_range"
                            + " CHECK (end_at IS NULL OR start_at < end_at)"
            );
            statement.execute(
                    "CREATE INDEX idx_tb_celebration_event_start_end"
                            + " ON tb_celebration_event (start_at, end_at)"
            );
        }
    }

    private void backfillCelebrationEventStartAt(Connection connection) throws SQLException {
        record Row(long id, LocalDate eventDate, LocalTime eventTime) {
        }
        java.util.List<Row> rows = new java.util.ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT id, event_date, event_time FROM tb_celebration_event")) {
            while (resultSet.next()) {
                Date eventDate = resultSet.getDate("event_date");
                Time eventTime = resultSet.getTime("event_time");
                rows.add(new Row(resultSet.getLong("id"), eventDate.toLocalDate(), eventTime.toLocalTime()));
            }
        }

        if (rows.isEmpty()) {
            return;
        }

        String sql = "UPDATE tb_celebration_event SET start_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Row row : rows) {
                LocalDateTime startAt = LocalDateTime.of(row.eventDate(), row.eventTime());
                statement.setTimestamp(1, Timestamp.valueOf(startAt));
                statement.setLong(2, row.id());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private boolean isH2(String databaseProductName) {
        return databaseProductName != null && databaseProductName.toUpperCase().contains("H2");
    }

    private boolean isMySql(String databaseProductName) {
        return databaseProductName != null && databaseProductName.toUpperCase().contains("MYSQL");
    }
}
