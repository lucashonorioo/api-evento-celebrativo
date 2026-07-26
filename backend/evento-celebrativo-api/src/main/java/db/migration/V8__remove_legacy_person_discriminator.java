package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.StringJoiner;

/**
 * Remove fisicamente o discriminator legado de tb_person: Person + PersonMinistry ja sao a
 * fonte oficial de escrita e classificacao ministerial desde as branches anteriores desta serie.
 * Antes de qualquer DDL, confirma que todo person_type historico com significado ministerial
 * possui um vinculo ativo correspondente em tb_person_ministry (mesmo mapeamento usado pelo
 * backfill V4); se houver pessoa com person_type divergente ou sem vinculo ativo correspondente,
 * a migration falha sem remover a coluna nem criar nenhum PersonMinistry automaticamente.
 */
public class V8__remove_legacy_person_discriminator extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        validateEveryPersonTypeHasActiveMinistry(connection);

        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE tb_person DROP COLUMN person_type");
        }
    }

    private void validateEveryPersonTypeHasActiveMinistry(Connection connection) throws SQLException {
        String sql = """
                SELECT p.id, p.person_type
                FROM tb_person p
                LEFT JOIN tb_person_ministry pm
                    ON pm.person_id = p.id
                    AND pm.active = TRUE
                    AND pm.ministry_type = CASE p.person_type
                        WHEN 'reader' THEN 'READER'
                        WHEN 'commentator' THEN 'COMMENTATOR'
                        WHEN 'priest' THEN 'PRIEST'
                        WHEN 'minister_of_the_word' THEN 'MINISTER_OF_THE_WORD'
                        WHEN 'eucharistic_minister' THEN 'EUCHARISTIC_MINISTER'
                    END
                WHERE pm.id IS NULL
                ORDER BY p.id
                """;

        StringJoiner divergent = new StringJoiner("; ");
        long divergentCount = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                divergentCount++;
                if (divergentCount <= 20) {
                    divergent.add("id=" + resultSet.getLong("id")
                            + ", person_type=" + resultSet.getString("person_type"));
                }
            }
        }

        if (divergentCount > 0) {
            String sample = divergentCount > 20
                    ? divergent + "; ... (" + (divergentCount - 20) + " pessoas adicionais omitidas)"
                    : divergent.toString();
            throw new FlywayException(
                    "Nao foi possivel remover person_type: " + divergentCount
                            + " pessoa(s) com person_type sem vinculo ativo correspondente em tb_person_ministry: "
                            + sample
            );
        }
    }
}
