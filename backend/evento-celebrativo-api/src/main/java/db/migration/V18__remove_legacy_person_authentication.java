package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Remove definitivamente a autenticacao legada de Person: a partir desta versao, UserAccount e
 * UserAccountRole passam a ser a unica fonte de credenciais e autorizacao; Person representa
 * somente a pessoa fisica.
 * <p>
 * Ordem obrigatoria de execucao (documentada aqui porque dialetos diferem em transacionalidade de
 * DDL; no MySQL, por exemplo, cada ALTER/DROP e efetivamente auto-commit):
 * <ol>
 *     <li>validar estrutura (tabelas/colunas/constraints existem, dialeto suportado);</li>
 *     <li>validar dados (paridade Person-legado vs UserAccount) - falha aqui NAO altera nada;</li>
 *     <li>adicionar UNIQUE(user_account_id) em tb_user_account_role;</li>
 *     <li>recriar fk_tb_user_account_person com ON DELETE RESTRICT (era CASCADE desde V13);</li>
 *     <li>remover tb_person_role;</li>
 *     <li>remover tb_person.password.</li>
 * </ol>
 * A fase destrutiva (passos 3-6) so comeca depois que TODAS as validacoes dos passos 1-2 passarem
 * para o banco inteiro. Nenhuma linha e corrigida ou criada automaticamente: qualquer inconsistencia
 * encontrada interrompe a migration antes do primeiro DDL, listando categoria, quantidade e uma
 * lista limitada de personIds (nunca hashes).
 */
public class V18__remove_legacy_person_authentication extends BaseJavaMigration {

    private static final String TB_PERSON = "tb_person";
    private static final String TB_PERSON_ROLE = "tb_person_role";
    private static final String TB_USER_ACCOUNT = "tb_user_account";
    private static final String TB_USER_ACCOUNT_ROLE = "tb_user_account_role";
    private static final String TB_ROLE = "tb_role";
    private static final int MAX_LISTED_IDS = 20;

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        boolean mysql = isMySql(connection);
        assertDialectSupported(connection, mysql);
        assertStructure(connection);
        String personAccountFkName = requirePersonAccountForeignKeyName(connection);

        assertNoLegacyInconsistencies(connection);

        addUniqueUserAccountRolePerAccount(connection);
        replacePersonAccountForeignKeyWithRestrict(connection, mysql, personAccountFkName);
        dropPersonRoleTable(connection);
        dropPersonPasswordColumn(connection);
    }

    // ---------------------------------------------------------------------
    // 1) Validacao estrutural (antes de qualquer DDL)
    // ---------------------------------------------------------------------

    private void assertDialectSupported(Connection connection, boolean mysql) throws SQLException {
        if (mysql) {
            return;
        }
        String productName = connection.getMetaData().getDatabaseProductName();
        String normalizedProductName = productName == null ? "" : productName.toUpperCase(Locale.ROOT);
        if (!normalizedProductName.contains("H2") && !normalizedProductName.contains("POSTGRESQL")) {
            throw new FlywayException(
                    "V18 so suporta H2, MySQL e PostgreSQL; dialeto detectado: " + productName
                            + ". Nenhuma alteracao foi realizada.");
        }
    }

    private void assertStructure(Connection connection) throws SQLException {
        requireTable(connection, TB_PERSON);
        requireColumn(connection, TB_PERSON, "password");
        requireColumn(connection, TB_PERSON, "active");
        requireTable(connection, TB_PERSON_ROLE);
        requireColumn(connection, TB_PERSON_ROLE, "person_id");
        requireColumn(connection, TB_PERSON_ROLE, "role_id");
        requireTable(connection, TB_USER_ACCOUNT);
        requireColumn(connection, TB_USER_ACCOUNT, "person_id");
        requireColumn(connection, TB_USER_ACCOUNT, "password_hash");
        requireTable(connection, TB_USER_ACCOUNT_ROLE);
        requireColumn(connection, TB_USER_ACCOUNT_ROLE, "user_account_id");
        requireColumn(connection, TB_USER_ACCOUNT_ROLE, "role_id");
        requireTable(connection, TB_ROLE);
        requireColumn(connection, TB_ROLE, "authority");
    }

    private void requireTable(Connection connection, String tableName) throws SQLException {
        if (!tableExists(connection, tableName)) {
            throw new FlywayException(
                    "V18 nao pode prosseguir: tabela " + tableName + " nao encontrada. Nenhuma alteracao foi realizada.");
        }
    }

    private void requireColumn(Connection connection, String tableName, String columnName) throws SQLException {
        if (!columnExists(connection, tableName, columnName)) {
            throw new FlywayException(
                    "V18 nao pode prosseguir: coluna " + tableName + "." + columnName
                            + " nao encontrada. Nenhuma alteracao foi realizada.");
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getTables(connection.getCatalog(), connection.getSchema(), "%", new String[]{"TABLE"})) {
            while (resultSet.next()) {
                if (tableName.equalsIgnoreCase(resultSet.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), connection.getSchema(), tableName, "%")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String requirePersonAccountForeignKeyName(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getImportedKeys(connection.getCatalog(), connection.getSchema(), TB_USER_ACCOUNT)) {
            while (resultSet.next()) {
                String pkTable = resultSet.getString("PKTABLE_NAME");
                String fkColumn = resultSet.getString("FKCOLUMN_NAME");
                if (TB_PERSON.equalsIgnoreCase(pkTable) && "person_id".equalsIgnoreCase(fkColumn)) {
                    return resultSet.getString("FK_NAME");
                }
            }
        }
        throw new FlywayException(
                "V18 nao pode prosseguir: FK de " + TB_USER_ACCOUNT + ".person_id para " + TB_PERSON
                        + " nao encontrada. Nenhuma alteracao foi realizada.");
    }

    // ---------------------------------------------------------------------
    // 2) Validacao de dados (antes de qualquer DDL)
    // ---------------------------------------------------------------------

    private void assertNoLegacyInconsistencies(Connection connection) throws SQLException {
        Map<String, List<Long>> violationsByCategory = new LinkedHashMap<>();

        putIfNotEmpty(violationsByCategory, "USER_ACCOUNT_ROLE_INVALID", findUserAccountRoleInvalid(connection));
        putIfNotEmpty(violationsByCategory, "LEGACY_PASSWORD_MISMATCH", findLegacyPasswordMismatch(connection));
        putIfNotEmpty(violationsByCategory, "LEGACY_ROLE_MISMATCH", findLegacyRoleMismatch(connection));
        putIfNotEmpty(violationsByCategory, "LEGACY_CREDENTIAL_WITHOUT_ACCOUNT", findLegacyCredentialWithoutAccount(connection));

        if (violationsByCategory.isEmpty()) {
            return;
        }

        StringBuilder message = new StringBuilder(
                "V18 nao pode prosseguir: inconsistencias entre autenticacao legada de Person e "
                        + "UserAccount foram encontradas. Nenhuma alteracao de schema foi realizada; "
                        + "corrija os dados conscientemente antes de repetir o upgrade.");
        violationsByCategory.forEach((category, ids) -> {
            message.append(" [").append(category).append(": ").append(ids.size()).append(" pessoa(s)");
            List<Long> limited = ids.size() > MAX_LISTED_IDS ? ids.subList(0, MAX_LISTED_IDS) : ids;
            message.append(", personIds=").append(limited);
            if (ids.size() > MAX_LISTED_IDS) {
                message.append(" (+").append(ids.size() - MAX_LISTED_IDS).append(" nao listado(s))");
            }
            message.append(']');
        });
        throw new FlywayException(message.toString());
    }

    private void putIfNotEmpty(Map<String, List<Long>> map, String category, List<Long> ids) {
        if (!ids.isEmpty()) {
            map.put(category, ids);
        }
    }

    /** Pessoa com conta cuja UserAccountRole nao tem exatamente uma role valida (ROLE_ADMIN/ROLE_OPERATOR). */
    private List<Long> findUserAccountRoleInvalid(Connection connection) throws SQLException {
        return queryIds(connection, """
                SELECT p.id
                FROM tb_person p
                JOIN tb_user_account ua ON ua.person_id = p.id
                WHERE (
                    SELECT COUNT(*) FROM tb_user_account_role uar WHERE uar.user_account_id = ua.id
                ) <> 1
                OR EXISTS (
                    SELECT 1 FROM tb_user_account_role uar
                    JOIN tb_role r ON r.id = uar.role_id
                    WHERE uar.user_account_id = ua.id
                      AND r.authority NOT IN ('ROLE_ADMIN', 'ROLE_OPERATOR')
                )
                ORDER BY p.id
                """);
    }

    /** Pessoa com conta cujo password legado esta nulo ou diverge do passwordHash. */
    private List<Long> findLegacyPasswordMismatch(Connection connection) throws SQLException {
        return queryIds(connection, """
                SELECT p.id
                FROM tb_person p
                JOIN tb_user_account ua ON ua.person_id = p.id
                WHERE p.password IS NULL OR p.password <> ua.password_hash
                ORDER BY p.id
                """);
    }

    /** Pessoa com conta cujas roles legadas (tb_person_role) divergem das roles da conta. */
    private List<Long> findLegacyRoleMismatch(Connection connection) throws SQLException {
        return queryIds(connection, """
                SELECT p.id
                FROM tb_person p
                JOIN tb_user_account ua ON ua.person_id = p.id
                WHERE EXISTS (
                    SELECT 1 FROM tb_person_role pr
                    WHERE pr.person_id = p.id
                      AND NOT EXISTS (
                          SELECT 1 FROM tb_user_account_role uar
                          WHERE uar.user_account_id = ua.id AND uar.role_id = pr.role_id
                      )
                )
                OR EXISTS (
                    SELECT 1 FROM tb_user_account_role uar
                    WHERE uar.user_account_id = ua.id
                      AND NOT EXISTS (
                          SELECT 1 FROM tb_person_role pr
                          WHERE pr.person_id = p.id AND pr.role_id = uar.role_id
                      )
                )
                ORDER BY p.id
                """);
    }

    /** Pessoa sem conta que ainda carrega password legado ou alguma tb_person_role. */
    private List<Long> findLegacyCredentialWithoutAccount(Connection connection) throws SQLException {
        return queryIds(connection, """
                SELECT p.id
                FROM tb_person p
                WHERE NOT EXISTS (SELECT 1 FROM tb_user_account ua WHERE ua.person_id = p.id)
                  AND (
                      p.password IS NOT NULL
                      OR EXISTS (SELECT 1 FROM tb_person_role pr WHERE pr.person_id = p.id)
                  )
                ORDER BY p.id
                """);
    }

    private List<Long> queryIds(Connection connection, String sql) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                ids.add(resultSet.getLong(1));
            }
        }
        return ids;
    }

    // ---------------------------------------------------------------------
    // 3-6) Fase destrutiva (somente apos validacao completa)
    // ---------------------------------------------------------------------

    private void addUniqueUserAccountRolePerAccount(Connection connection) throws SQLException {
        execute(connection,
                "ALTER TABLE tb_user_account_role "
                        + "ADD CONSTRAINT uk_tb_user_account_role_user_account UNIQUE (user_account_id)");
    }

    private void replacePersonAccountForeignKeyWithRestrict(Connection connection, boolean mysql, String fkName) throws SQLException {
        String dropSql = mysql
                ? "ALTER TABLE tb_user_account DROP FOREIGN KEY " + fkName
                : "ALTER TABLE tb_user_account DROP CONSTRAINT " + fkName;
        execute(connection, dropSql);
        execute(connection, """
                ALTER TABLE tb_user_account
                    ADD CONSTRAINT fk_tb_user_account_person
                    FOREIGN KEY (person_id)
                    REFERENCES tb_person (id)
                    ON DELETE RESTRICT
                """);
    }

    private void dropPersonRoleTable(Connection connection) throws SQLException {
        execute(connection, "DROP TABLE tb_person_role");
    }

    private void dropPersonPasswordColumn(Connection connection) throws SQLException {
        execute(connection, "ALTER TABLE tb_person DROP COLUMN password");
    }

    private boolean isMySql(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName();
        return productName != null && productName.toUpperCase(Locale.ROOT).contains("MYSQL");
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
