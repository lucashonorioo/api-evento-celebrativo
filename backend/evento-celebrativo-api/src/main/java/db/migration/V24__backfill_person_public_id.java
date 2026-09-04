package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

public class V24__backfill_person_public_id extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        boolean binaryPublicId = isBinaryPublicId(connection);

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
                        connection.prepareStatement(selectSql);
                PreparedStatement updateStatement =
                        connection.prepareStatement(updateSql);
                ResultSet resultSet = selectStatement.executeQuery()
        ) {
            while (resultSet.next()) {
                long personId = resultSet.getLong("id");
                UUID publicId = UUID.randomUUID();

                if (binaryPublicId) {
                    updateStatement.setBytes(1, uuidToBytes(publicId));
                } else {
                    updateStatement.setObject(1, publicId);
                }
                updateStatement.setLong(2, personId);
                updateStatement.addBatch();
            }

            updateStatement.executeBatch();
        }
    }

    private boolean isBinaryPublicId(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(connection.getCatalog(), connection.getSchema(), "tb_person", "public_id")) {
            if (columns.next()) {
                int dataType = columns.getInt("DATA_TYPE");
                return dataType == Types.BINARY
                        || dataType == Types.VARBINARY
                        || dataType == Types.LONGVARBINARY;
            }
        }
        return false;
    }

    private byte[] uuidToBytes(UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }
}
