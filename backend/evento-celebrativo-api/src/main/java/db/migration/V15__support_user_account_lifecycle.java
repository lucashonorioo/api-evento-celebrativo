package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * Suporte ao ciclo de vida de Person/UserAccount.
 * <p>
 * A partir desta versao, uma Person pode existir sem conta de acesso; por isso o hash legado em
 * tb_person.password precisa aceitar NULL. UserAccount passa a ter token_version tecnico, usado
 * para invalidacao deterministica de tokens sem expor o valor em DTOs administrativos.
 */
public class V15__support_user_account_lifecycle extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        allowNullablePersonPassword(connection);
        addTokenVersion(connection);
        addTokenVersionCheck(connection);
    }

    private void allowNullablePersonPassword(Connection connection) throws SQLException {
        String sql = isMySql(connection)
                ? "ALTER TABLE tb_person MODIFY password VARCHAR(255) NULL"
                : "ALTER TABLE tb_person ALTER COLUMN password VARCHAR(255) NULL";
        execute(connection, sql);
    }

    private void addTokenVersion(Connection connection) throws SQLException {
        execute(connection, "ALTER TABLE tb_user_account ADD token_version BIGINT NOT NULL DEFAULT 0");
    }

    private void addTokenVersionCheck(Connection connection) throws SQLException {
        execute(connection, """
                ALTER TABLE tb_user_account
                    ADD CONSTRAINT ck_tb_user_account_token_version_non_negative
                    CHECK (token_version >= 0)
                """);
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
