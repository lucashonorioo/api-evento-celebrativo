package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobertura dedicada da migration V18: remocao definitiva da autenticacao legada de Person
 * (tb_person.password, tb_person_role) e promocao de UserAccount/UserAccountRole a unica fonte de
 * credenciais/autorizacao. V1-V17 permanecem intactas (verificado indiretamente pelo upgrade a
 * partir de V17 preservando dados existentes). Nenhuma validacao aqui corrige dados automaticamente;
 * cada cenario invalido deve interromper a migration antes do primeiro DDL destrutivo, deixando
 * tb_person_role e tb_person.password intactos.
 */
class V18RemoveLegacyPersonAuthenticationMigrationIntegrationTest {

    @Test
    void shouldMigrateCleanDatabaseThroughV18() {
        DataSource dataSource = createDataSource("v18_clean_database");
        MigrateResult result = migrateAll(dataSource, "18");

        assertEquals(18, result.migrationsExecuted);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        assertFalse(tableExists(jdbcTemplate, "tb_person_role"));
        assertFalse(columnExists(jdbcTemplate, "tb_person", "password"));
        assertTrue(tableExists(jdbcTemplate, "tb_role"));
        assertTrue(tableExists(jdbcTemplate, "tb_user_account"));
        assertTrue(tableExists(jdbcTemplate, "tb_user_account_role"));
        assertConstraintExists(jdbcTemplate, "tb_user_account_role", "uk_tb_user_account_role_user_account");
    }

    @Test
    void shouldUpgradeFromV17ToV18PreservingConsistentData() {
        DataSource dataSource = createDataSource("v18_upgrade_valid_data");
        migrateAll(dataSource, "17");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long personWithAccountId = insertPerson(jdbcTemplate, "Pessoa Com Conta", "encoded-hash");
        long accountId = insertUserAccount(jdbcTemplate, personWithAccountId, "34999999991", "encoded-hash");
        insertUserAccountRole(jdbcTemplate, accountId, 1L);
        insertPersonRole(jdbcTemplate, personWithAccountId, 1L);

        long personWithoutAccountId = insertPerson(jdbcTemplate, "Pessoa Sem Conta", null);

        MigrateResult result = migrateAll(dataSource, "18");

        assertEquals(1, result.migrationsExecuted);
        assertEquals(1, countRows(jdbcTemplate, "tb_user_account"));
        assertEquals(1, countRows(jdbcTemplate, "tb_user_account_role"));
        assertEquals(
                "Pessoa Com Conta",
                jdbcTemplate.queryForObject("SELECT name FROM tb_person WHERE id = ?", String.class, personWithAccountId));
        assertEquals(
                "Pessoa Sem Conta",
                jdbcTemplate.queryForObject("SELECT name FROM tb_person WHERE id = ?", String.class, personWithoutAccountId));
        assertFalse(tableExists(jdbcTemplate, "tb_person_role"));
        assertFalse(columnExists(jdbcTemplate, "tb_person", "password"));
    }

    @Test
    void shouldBlockUpgradeWhenLegacyPasswordDivergesFromAccountHash() {
        DataSource dataSource = createDataSource("v18_password_mismatch");
        migrateAll(dataSource, "17");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long personId = insertPerson(jdbcTemplate, "Hash Divergente", "legacy-hash");
        long accountId = insertUserAccount(jdbcTemplate, personId, "34999999992", "different-hash");
        insertUserAccountRole(jdbcTemplate, accountId, 1L);
        insertPersonRole(jdbcTemplate, personId, 1L);

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource, "18"));

        assertTrue(hasMessageContaining(exception, "LEGACY_PASSWORD_MISMATCH"));
        assertTrue(hasMessageContaining(exception, "personIds=[" + personId + "]"));
        assertNoDestructiveChangeApplied(jdbcTemplate);
    }

    @Test
    void shouldBlockUpgradeWhenLegacyRoleDivergesFromAccountRole() {
        DataSource dataSource = createDataSource("v18_role_mismatch");
        migrateAll(dataSource, "17");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long personId = insertPerson(jdbcTemplate, "Role Divergente", "encoded-hash");
        long accountId = insertUserAccount(jdbcTemplate, personId, "34999999993", "encoded-hash");
        insertUserAccountRole(jdbcTemplate, accountId, 1L);
        // tb_person_role aponta ROLE_ADMIN (2), enquanto a conta tem ROLE_OPERATOR (1).
        insertPersonRole(jdbcTemplate, personId, 2L);

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource, "18"));

        assertTrue(hasMessageContaining(exception, "LEGACY_ROLE_MISMATCH"));
        assertNoDestructiveChangeApplied(jdbcTemplate);
    }

    @Test
    void shouldBlockUpgradeWhenCredentialExistsWithoutAccount() {
        DataSource dataSource = createDataSource("v18_credential_without_account");
        migrateAll(dataSource, "17");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long personId = insertPerson(jdbcTemplate, "Credencial Orfa", "encoded-hash");

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource, "18"));

        assertTrue(hasMessageContaining(exception, "LEGACY_CREDENTIAL_WITHOUT_ACCOUNT"));
        assertTrue(hasMessageContaining(exception, "personIds=[" + personId + "]"));
        assertNoDestructiveChangeApplied(jdbcTemplate);
    }

    /**
     * Lado da condicao LEGACY_CREDENTIAL_WITHOUT_ACCOUNT independente de
     * {@link #shouldBlockUpgradeWhenCredentialExistsWithoutAccount()}: aqui password e nulo e a
     * unica credencial orfa e a role legada em tb_person_role, provando que a query de validacao
     * (password IS NOT NULL OR EXISTS person_role) dispara pelo segundo ramo do OR isoladamente.
     */
    @Test
    void shouldBlockUpgradeWhenLegacyRoleExistsWithoutAccountAndWithoutPassword() {
        DataSource dataSource = createDataSource("v18_role_without_account");
        migrateAll(dataSource, "17");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long personId = insertPerson(jdbcTemplate, "Role Orfa Sem Senha", null);
        insertPersonRole(jdbcTemplate, personId, 1L);

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource, "18"));

        assertTrue(hasMessageContaining(exception, "LEGACY_CREDENTIAL_WITHOUT_ACCOUNT"));
        assertTrue(hasMessageContaining(exception, "personIds=[" + personId + "]"));
        assertFalse(hasMessageContaining(exception, "LEGACY_PASSWORD_MISMATCH"));
        assertFalse(hasMessageContaining(exception, "LEGACY_ROLE_MISMATCH"));
        assertFalse(hasMessageContaining(exception, "USER_ACCOUNT_ROLE_INVALID"));
        assertNoDestructiveChangeApplied(jdbcTemplate);
        assertEquals(1, countRows(jdbcTemplate, "tb_person_role"));
        assertEquals(
                "Role Orfa Sem Senha",
                jdbcTemplate.queryForObject("SELECT name FROM tb_person WHERE id = ?", String.class, personId));
    }

    @Test
    void shouldBlockUpgradeWhenAccountHasZeroRoles() {
        DataSource dataSource = createDataSource("v18_zero_roles");
        migrateAll(dataSource, "17");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long personId = insertPerson(jdbcTemplate, "Conta Sem Role", "encoded-hash");
        insertUserAccount(jdbcTemplate, personId, "34999999994", "encoded-hash");
        // Nenhuma tb_user_account_role inserida; tb_person_role tambem vazia (par consistente
        // "zero roles"), entao o unico invariante violado e USER_ACCOUNT_ROLE_INVALID.

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource, "18"));

        assertTrue(hasMessageContaining(exception, "USER_ACCOUNT_ROLE_INVALID"));
        assertNoDestructiveChangeApplied(jdbcTemplate);
    }

    @Test
    void shouldBlockUpgradeWhenAccountHasTwoRoles() {
        DataSource dataSource = createDataSource("v18_two_roles");
        migrateAll(dataSource, "17");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long personId = insertPerson(jdbcTemplate, "Conta Duas Roles", "encoded-hash");
        long accountId = insertUserAccount(jdbcTemplate, personId, "34999999995", "encoded-hash");
        insertUserAccountRole(jdbcTemplate, accountId, 1L);
        insertUserAccountRole(jdbcTemplate, accountId, 2L);
        insertPersonRole(jdbcTemplate, personId, 1L);
        insertPersonRole(jdbcTemplate, personId, 2L);

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource, "18"));

        assertTrue(hasMessageContaining(exception, "USER_ACCOUNT_ROLE_INVALID"));
        assertNoDestructiveChangeApplied(jdbcTemplate);
    }

    /**
     * Isola USER_ACCOUNT_ROLE_INVALID por authority desconhecida (fora de ROLE_ADMIN/ROLE_OPERATOR)
     * de LEGACY_ROLE_MISMATCH: tb_person_role espelha exatamente a mesma role (ainda que invalida)
     * atribuida a tb_user_account_role, entao a comparacao person-vs-conta nao diverge e o unico
     * invariante violado e a authority em si. tb_role nao tem constraint que restrinja authorities
     * a um conjunto fixo, entao o estado e montado por INSERT direto, reproduzindo uma inconsistencia
     * historica real sem enfraquecer nenhuma constraint da V18.
     */
    @Test
    void shouldBlockUpgradeWhenAccountRoleAuthorityIsUnknown() {
        DataSource dataSource = createDataSource("v18_unknown_authority");
        migrateAll(dataSource, "17");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long personId = insertPerson(jdbcTemplate, "Authority Desconhecida", "encoded-hash");
        long accountId = insertUserAccount(jdbcTemplate, personId, "34999999998", "encoded-hash");
        long unknownRoleId = insertRole(jdbcTemplate, "ROLE_GUEST");
        insertUserAccountRole(jdbcTemplate, accountId, unknownRoleId);
        insertPersonRole(jdbcTemplate, personId, unknownRoleId);

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource, "18"));

        assertTrue(hasMessageContaining(exception, "USER_ACCOUNT_ROLE_INVALID"));
        assertTrue(hasMessageContaining(exception, "personIds=[" + personId + "]"));
        assertFalse(hasMessageContaining(exception, "LEGACY_ROLE_MISMATCH"));
        assertFalse(hasMessageContaining(exception, "LEGACY_PASSWORD_MISMATCH"));
        assertFalse(hasMessageContaining(exception, "LEGACY_CREDENTIAL_WITHOUT_ACCOUNT"));
        assertNoDestructiveChangeApplied(jdbcTemplate);
        assertEquals(
                "ROLE_GUEST",
                jdbcTemplate.queryForObject("SELECT authority FROM tb_role WHERE id = ?", String.class, unknownRoleId));
    }

    @Test
    void shouldReplacePersonAccountForeignKeyWithRestrictOnDelete() {
        DataSource dataSource = createDataSource("v18_fk_restrict");
        migrateAll(dataSource, "18");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long personId = insertPersonPostV18(jdbcTemplate, "Pessoa Com Conta Protegida");
        insertUserAccount(jdbcTemplate, personId, "34999999996", "encoded-hash");

        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", personId));
    }

    @Test
    void shouldRejectSecondRoleForSameAccountAfterMigration() {
        DataSource dataSource = createDataSource("v18_unique_account_role");
        migrateAll(dataSource, "18");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long personId = insertPersonPostV18(jdbcTemplate, "Conta Unica Role");
        long accountId = insertUserAccount(jdbcTemplate, personId, "34999999997", "encoded-hash");
        insertUserAccountRole(jdbcTemplate, accountId, 1L);

        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> insertUserAccountRole(jdbcTemplate, accountId, 2L));
    }

    private void assertNoDestructiveChangeApplied(JdbcTemplate jdbcTemplate) {
        assertTrue(tableExists(jdbcTemplate, "tb_person_role"));
        assertTrue(columnExists(jdbcTemplate, "tb_person", "password"));
        assertFalse(constraintExists(jdbcTemplate, "tb_user_account_role", "uk_tb_user_account_role_user_account"));
    }

    private boolean hasMessageContaining(Throwable throwable, String expectedText) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(expectedText)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private long insertPersonPostV18(JdbcTemplate jdbcTemplate, String name) {
        String phoneNumber = "34987" + String.format("%06d", Math.floorMod(UUID.randomUUID().hashCode(), 1_000_000));
        jdbcTemplate.update("INSERT INTO tb_person(name, phone_number) VALUES (?, ?)", name, phoneNumber);
        return jdbcTemplate.queryForObject("SELECT id FROM tb_person WHERE phone_number = ?", Long.class, phoneNumber);
    }

    private long insertPerson(JdbcTemplate jdbcTemplate, String name, String legacyPassword) {
        String phoneNumber = "34987" + String.format("%06d", Math.floorMod(UUID.randomUUID().hashCode(), 1_000_000));
        jdbcTemplate.update(
                "INSERT INTO tb_person(name, phone_number, password) VALUES (?, ?, ?)",
                name, phoneNumber, legacyPassword);
        return jdbcTemplate.queryForObject("SELECT id FROM tb_person WHERE phone_number = ?", Long.class, phoneNumber);
    }

    private long insertUserAccount(JdbcTemplate jdbcTemplate, long personId, String username, String passwordHash) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_user_account(person_id, username, password_hash, enabled, created_at, updated_at)
                VALUES (?, ?, ?, TRUE, CURRENT_TIMESTAMP(0), CURRENT_TIMESTAMP(0))
                """,
                personId, username, passwordHash);
        return jdbcTemplate.queryForObject("SELECT id FROM tb_user_account WHERE person_id = ?", Long.class, personId);
    }

    private void insertUserAccountRole(JdbcTemplate jdbcTemplate, long accountId, long roleId) {
        jdbcTemplate.update(
                "INSERT INTO tb_user_account_role(user_account_id, role_id) VALUES (?, ?)", accountId, roleId);
    }

    private void insertPersonRole(JdbcTemplate jdbcTemplate, long personId, long roleId) {
        jdbcTemplate.update("INSERT INTO tb_person_role(person_id, role_id) VALUES (?, ?)", personId, roleId);
    }

    private long insertRole(JdbcTemplate jdbcTemplate, String authority) {
        long roleId = 999L;
        jdbcTemplate.update("INSERT INTO tb_role(id, authority) VALUES (?, ?)", roleId, authority);
        return jdbcTemplate.queryForObject("SELECT id FROM tb_role WHERE authority = ?", Long.class, authority);
    }

    private int countRows(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count == null ? 0 : count;
    }

    private boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = LOWER(?)",
                Integer.class, tableName);
        return count != null && count > 0;
    }

    private boolean columnExists(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE LOWER(table_name) = LOWER(?) AND LOWER(column_name) = LOWER(?)",
                Integer.class, tableName, columnName);
        return count != null && count > 0;
    }

    private boolean constraintExists(JdbcTemplate jdbcTemplate, String tableName, String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE LOWER(table_name) = LOWER(?) AND LOWER(constraint_name) = LOWER(?)",
                Integer.class, tableName, constraintName);
        return count != null && count > 0;
    }

    private void assertConstraintExists(JdbcTemplate jdbcTemplate, String tableName, String constraintName) {
        assertTrue(constraintExists(jdbcTemplate, tableName, constraintName));
    }

    private DataSource createDataSource(String namePrefix) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + namePrefix + "_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private MigrateResult migrateAll(DataSource dataSource, String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }
}
