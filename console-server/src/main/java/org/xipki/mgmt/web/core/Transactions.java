package org.xipki.mgmt.web.core;

import java.sql.*;
import java.util.function.*;

/** One transaction per thread; nested operations participate and cannot hide a rollback. */
public final class Transactions {
    private final Jdbc jdbc;
    public Transactions(Jdbc jdbc) { this.jdbc=jdbc; }
    public static final class Status {
        private final Jdbc.Transaction transaction;
        private Status(Jdbc.Transaction transaction) { this.transaction=transaction; }
        public void setRollbackOnly() { transaction.rollbackOnly=true; }
    }
    public <T> T execute(Function<Status,T> work) {
        Jdbc.Transaction existing=jdbc.current.get();
        if(existing!=null) {
            try { return work.apply(new Status(existing)); }
            catch(RuntimeException | Error ex) { existing.rollbackOnly=true; throw ex; }
        }
        try(Connection connection=jdbc.getDataSource().getConnection()) {
            boolean autoCommit=connection.getAutoCommit(); connection.setAutoCommit(false);
            Jdbc.Transaction transaction=new Jdbc.Transaction(connection); jdbc.current.set(transaction);
            try {
                T result=work.apply(new Status(transaction));
                if(transaction.rollbackOnly) throw new Jdbc.DataAccessException("Transaction marked for rollback",null);
                connection.commit(); return result;
            } catch(RuntimeException | Error | SQLException ex) {
                try { connection.rollback(); } catch(SQLException rollback) { ex.addSuppressed(rollback); }
                if(ex instanceof SQLException sql) throw Jdbc.failure(sql);
                throw ex;
            } finally {
                jdbc.current.remove();
                try { connection.setAutoCommit(autoCommit); } catch(SQLException ignored) { /* Connection is closed immediately. */ }
            }
        } catch(SQLException ex) { throw Jdbc.failure(ex); }
    }
    public void executeWithoutResult(Consumer<Status> work) { execute(status -> { work.accept(status); return null; }); }
}
