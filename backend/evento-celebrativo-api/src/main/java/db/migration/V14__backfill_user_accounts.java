package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.StringJoiner;

/**
 * Backfill de tb_user_account/tb_user_account_role a partir do estado atual de tb_person e
 * tb_person_role (fonte de verdade legada, preservada por esta branch).
 * <p>
 * Estrategia de timestamp (secao 12 do requisito): tb_person.created_at/updated_at existem desde
 * V3, mas nunca foram mapeados por nenhuma entidade Java nem gerenciados pela aplicacao - para
 * linhas ja existentes antes de V3, ambos os campos recebem o mesmo instante (o momento em que
 * V3 foi aplicada), o que nao representa a criacao real da pessoa. Por nao serem confiaveis como
 * timestamp de criacao, esta migration NAO os reutiliza; em vez disso captura um unico
 * currentSecond (sem nanos) no inicio da execucao e usa exatamente esse valor para created_at e
 * updated_at de todas as contas criadas neste backfill.
 */
public class V14__backfill_user_accounts extends BaseJavaMigration {

    private static final int MAX_SAMPLE_IDS = 20;

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        validateCredentials(connection);
        validateNoDuplicateUsernames(connection);
        validatePersonRolesReferenceExistingRoles(connection);
        validateNoPreexistingUserAccounts(connection);

        LocalDateTime currentSecond = LocalDateTime.now().withNano(0);
        Timestamp backfillTimestamp = Timestamp.valueOf(currentSecond);

        backfillUserAccounts(connection, backfillTimestamp);
        backfillUserAccountRoles(connection);

        validateAccountCountMatchesPersonCount(connection);
        validateUsernameParity(connection);
        validatePasswordHashParity(connection);
        validateRoleParity(connection);
        validateAllAccountsEnabled(connection);
        validateNoAdministratorLost(connection);
    }

    private void validateCredentials(Connection connection) throws SQLException {
        String sql = """
                SELECT id
                FROM tb_person
                WHERE phone_number IS NULL OR TRIM(phone_number) = ''
                   OR password IS NULL OR TRIM(password) = ''
                ORDER BY id
                """;
        failIfAnyMatch(connection, sql,
                "Nao foi possivel realizar o backfill de tb_user_account: pessoa(s) com telefone ou senha ausente/vazio");
    }

    private void validateNoDuplicateUsernames(Connection connection) throws SQLException {
        String sql = """
                SELECT MIN(id) AS id
                FROM tb_person
                GROUP BY phone_number
                HAVING COUNT(*) > 1
                """;
        failIfAnyMatch(connection, sql,
                "Nao foi possivel realizar o backfill de tb_user_account: telefone duplicado entre pessoas");
    }

    private void validatePersonRolesReferenceExistingRoles(Connection connection) throws SQLException {
        String sql = """
                SELECT pr.person_id AS id
                FROM tb_person_role pr
                LEFT JOIN tb_role r ON r.id = pr.role_id
                WHERE r.id IS NULL
                ORDER BY pr.person_id
                """;
        failIfAnyMatch(connection, sql,
                "Nao foi possivel realizar o backfill de tb_user_account: tb_person_role referencia role inexistente para a(s) pessoa(s)");
    }

    private void validateNoPreexistingUserAccounts(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) AS total FROM tb_user_account")) {
            resultSet.next();
            long total = resultSet.getLong("total");
            if (total > 0) {
                throw new FlywayException(
                        "Nao foi possivel realizar o backfill de tb_user_account: ja existem " + total
                                + " conta(s) paralela(s) preexistente(s), estado incompativel com este backfill inicial");
            }
        }
    }

    private void backfillUserAccounts(Connection connection, Timestamp backfillTimestamp) throws SQLException {
        String sql = """
                INSERT INTO tb_user_account (person_id, username, password_hash, enabled, created_at, updated_at)
                SELECT p.id, p.phone_number, p.password, TRUE, ?, ?
                FROM tb_person p
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, backfillTimestamp);
            statement.setTimestamp(2, backfillTimestamp);
            statement.executeUpdate();
        }
    }

    private void backfillUserAccountRoles(Connection connection) throws SQLException {
        String sql = """
                INSERT INTO tb_user_account_role (user_account_id, role_id)
                SELECT ua.id, pr.role_id
                FROM tb_person_role pr
                JOIN tb_user_account ua ON ua.person_id = pr.person_id
                """;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private void validateAccountCountMatchesPersonCount(Connection connection) throws SQLException {
        String sql = """
                SELECT
                    (SELECT COUNT(*) FROM tb_person) AS person_count,
                    (SELECT COUNT(*) FROM tb_user_account) AS account_count
                """;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            long personCount = resultSet.getLong("person_count");
            long accountCount = resultSet.getLong("account_count");
            if (personCount != accountCount) {
                throw new FlywayException(
                        "Backfill de tb_user_account inconsistente: " + personCount + " pessoa(s) e "
                                + accountCount + " conta(s) apos o backfill");
            }
        }
    }

    private void validateUsernameParity(Connection connection) throws SQLException {
        String sql = """
                SELECT p.id AS id
                FROM tb_person p
                JOIN tb_user_account ua ON ua.person_id = p.id
                WHERE ua.username <> p.phone_number
                ORDER BY p.id
                """;
        failIfAnyMatch(connection, sql,
                "Backfill de tb_user_account inconsistente: username divergente do telefone para a(s) pessoa(s)");
    }

    private void validatePasswordHashParity(Connection connection) throws SQLException {
        String sql = """
                SELECT p.id AS id
                FROM tb_person p
                JOIN tb_user_account ua ON ua.person_id = p.id
                WHERE ua.password_hash <> p.password
                ORDER BY p.id
                """;
        failIfAnyMatch(connection, sql,
                "Backfill de tb_user_account inconsistente: password_hash divergente do hash legado para a(s) pessoa(s)");
    }

    private void validateRoleParity(Connection connection) throws SQLException {
        String countsSql = """
                SELECT
                    (SELECT COUNT(*) FROM tb_person_role) AS legacy_count,
                    (SELECT COUNT(*) FROM tb_user_account_role) AS parallel_count
                """;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(countsSql)) {
            resultSet.next();
            long legacyCount = resultSet.getLong("legacy_count");
            long parallelCount = resultSet.getLong("parallel_count");
            if (legacyCount != parallelCount) {
                throw new FlywayException(
                        "Backfill de tb_user_account_role inconsistente: " + legacyCount
                                + " associacao(oes) em tb_person_role e " + parallelCount
                                + " em tb_user_account_role apos o backfill");
            }
        }

        String mismatchSql = """
                SELECT p.id AS id
                FROM tb_person p
                JOIN tb_user_account ua ON ua.person_id = p.id
                WHERE (SELECT COUNT(*) FROM tb_person_role pr WHERE pr.person_id = p.id)
                      <> (SELECT COUNT(*) FROM tb_user_account_role uar WHERE uar.user_account_id = ua.id)
                   OR EXISTS (
                        SELECT 1 FROM tb_person_role pr
                        WHERE pr.person_id = p.id
                          AND NOT EXISTS (
                              SELECT 1 FROM tb_user_account_role uar
                              WHERE uar.user_account_id = ua.id AND uar.role_id = pr.role_id
                          )
                   )
                ORDER BY p.id
                """;
        failIfAnyMatch(connection, mismatchSql,
                "Backfill de tb_user_account_role inconsistente: conjunto de roles divergente do legado para a(s) pessoa(s)");
    }

    private void validateAllAccountsEnabled(Connection connection) throws SQLException {
        String sql = "SELECT person_id AS id FROM tb_user_account WHERE enabled <> TRUE ORDER BY person_id";
        failIfAnyMatch(connection, sql,
                "Backfill de tb_user_account inconsistente: conta(s) criada(s) desabilitada(s) para a(s) pessoa(s)");
    }

    private void validateNoAdministratorLost(Connection connection) throws SQLException {
        String sql = """
                SELECT DISTINCT pr.person_id AS id
                FROM tb_person_role pr
                JOIN tb_role r ON r.id = pr.role_id
                JOIN tb_user_account ua ON ua.person_id = pr.person_id
                WHERE r.authority = 'ROLE_ADMIN'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM tb_user_account_role uar
                      JOIN tb_role r2 ON r2.id = uar.role_id
                      WHERE uar.user_account_id = ua.id AND r2.authority = 'ROLE_ADMIN'
                  )
                ORDER BY pr.person_id
                """;
        failIfAnyMatch(connection, sql,
                "Backfill de tb_user_account_role inconsistente: administrador perdido para a(s) pessoa(s)");
    }

    private void failIfAnyMatch(Connection connection, String sql, String messagePrefix) throws SQLException {
        StringJoiner affectedIds = new StringJoiner(", ");
        long affectedCount = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                affectedCount++;
                if (affectedCount <= MAX_SAMPLE_IDS) {
                    affectedIds.add(String.valueOf(resultSet.getLong("id")));
                }
            }
        }
        if (affectedCount > 0) {
            String sample = affectedCount > MAX_SAMPLE_IDS
                    ? affectedIds + "; ... (" + (affectedCount - MAX_SAMPLE_IDS) + " id(s) adicional(is) omitido(s))"
                    : affectedIds.toString();
            throw new FlywayException(messagePrefix + ". IDs afetados: " + sample);
        }
    }
}
