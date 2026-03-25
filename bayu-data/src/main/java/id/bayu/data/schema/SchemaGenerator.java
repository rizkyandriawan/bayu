package id.bayu.data.schema;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class SchemaGenerator {

    private final DataSource dataSource;

    public SchemaGenerator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void execute(List<String> ddlStatements) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String ddl : ddlStatements) {
                stmt.execute(ddl);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute schema DDL", e);
        }
    }
}
