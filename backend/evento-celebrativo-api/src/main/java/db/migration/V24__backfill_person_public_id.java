package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class V24__backfill_person_public_id extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        String selectSql = """
                SELECT id
                FROM tb_person
                WHERE public_id IS NULL
                ORDER BY id
                """;

        String updateSql = """
                UPDATE tb_person
                SET public_id = ?
                WHERE id = ?
                  AND public_id IS NULL
                """;

        try (
                PreparedStatement selectStatement =
                        context.getConnection().prepareStatement(selectSql);
                PreparedStatement updateStatement =
                        context.getConnection().prepareStatement(updateSql);
                ResultSet resultSet = selectStatement.executeQuery()
        ) {
            while (resultSet.next()) {
                long personId = resultSet.getLong("id");
                UUID publicId = UUID.randomUUID();

                updateStatement.setBytes(1, uuidToBytes(publicId));
                updateStatement.setLong(2, personId);
                updateStatement.addBatch();
            }

            updateStatement.executeBatch();
        }
    }

    private byte[] uuidToBytes(UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }
}