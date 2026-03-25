package id.bayu.data.transaction;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class TransactionManager {

    private static final ThreadLocal<Connection> currentConnection = new ThreadLocal<>();
    private final DataSource dataSource;

    public TransactionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Connection getConnection() throws SQLException {
        Connection conn = currentConnection.get();
        if (conn != null && !conn.isClosed()) {
            return conn; // Reuse transactional connection
        }
        return dataSource.getConnection();
    }

    public void begin() throws SQLException {
        Connection conn = dataSource.getConnection();
        conn.setAutoCommit(false);
        currentConnection.set(conn);
    }

    public void commit() throws SQLException {
        Connection conn = currentConnection.get();
        if (conn != null) {
            try {
                conn.commit();
            } finally {
                conn.setAutoCommit(true);
                conn.close();
                currentConnection.remove();
            }
        }
    }

    public void rollback() {
        Connection conn = currentConnection.get();
        if (conn != null) {
            try {
                conn.rollback();
                conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException ignored) {
            } finally {
                currentConnection.remove();
            }
        }
    }

    public boolean isInTransaction() {
        Connection conn = currentConnection.get();
        try {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public <T> T executeInTransaction(TransactionCallback<T> callback) {
        try {
            begin();
            T result = callback.execute();
            commit();
            return result;
        } catch (Exception e) {
            rollback();
            throw new RuntimeException(e);
        }
    }

    public void executeInTransaction(Runnable runnable) {
        try {
            begin();
            runnable.run();
            commit();
        } catch (Exception e) {
            rollback();
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    public interface TransactionCallback<T> {
        T execute() throws Exception;
    }
}
