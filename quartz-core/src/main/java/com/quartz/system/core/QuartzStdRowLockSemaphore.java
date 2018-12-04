package com.quartz.system.core;

import org.quartz.impl.jdbcjobstore.DBSemaphore;
import org.quartz.impl.jdbcjobstore.LockException;
import org.quartz.impl.jdbcjobstore.Util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @packageName：com.quartz.system.core
 * @desrciption: 重新quartz行级加锁模式
 * @author: gaowei
 * @date： 2018-12-04 9:31
 * @history: (version) author date desc
 */
public class QuartzStdRowLockSemaphore extends DBSemaphore{


    public static final String SELECT_FOR_LOCK = "SELECT * FROM "
            + TABLE_PREFIX_SUBST + TABLE_LOCKS + " WHERE " + QuartzJDBCConstants.COL_SCHED_NAME + " = ? "
            + " AND " + COL_LOCK_NAME + " = ? FOR UPDATE";

    public static final String INSERT_LOCK = "INSERT INTO "
            + TABLE_PREFIX_SUBST + TABLE_LOCKS + "(" + QuartzJDBCConstants.COL_SCHED_NAME + ", " + COL_LOCK_NAME + ") VALUES (?, ?)";

    public String scheduleName;

    public String insertSql;

    public QuartzStdRowLockSemaphore(String tablePrefix, String sql, String defaultSQL) {
        super(tablePrefix, sql, SELECT_FOR_LOCK);
    }

    public QuartzStdRowLockSemaphore(String tablePrefix, String scheduleName) {
        super(tablePrefix, null, SELECT_FOR_LOCK);
        this.scheduleName = scheduleName;
        insertSql = INSERT_LOCK;
        insertSql = Util.rtp(this.insertSql, getTablePrefix());
    }

    @Override
    protected void executeSQL(Connection conn, String lockName, String expandedSQL) throws LockException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        SQLException initCause = null;

        // attempt lock two times (to work-around possible race conditions in inserting the lock row the first time running)
        int count = 0;
        do {
            count++;
            try {
                ps = conn.prepareStatement(expandedSQL);
                ps.setString(1, scheduleName);
                ps.setString(2, lockName);

                if (getLog().isDebugEnabled()) {
                    getLog().debug(
                            "Lock '" + lockName + "' is being obtained: " +
                                    Thread.currentThread().getName());
                }
                rs = ps.executeQuery();
                if (!rs.next()) {
                    getLog().debug(
                            "Inserting new lock row for lock: '" + lockName + "' being obtained by thread: " +
                                    Thread.currentThread().getName());
                    rs.close();
                    rs = null;
                    ps.close();
                    ps = null;
                    ps = conn.prepareStatement(insertSql);
                    ps.setString(1, scheduleName);
                    ps.setString(2, lockName);

                    int res = ps.executeUpdate();

                    if(res != 1) {
                        if(count < 3) {
                            // pause a bit to give another thread some time to commit the insert of the new lock row
                            try {
                                Thread.sleep(1000L);
                            } catch (InterruptedException ignore) {
                                Thread.currentThread().interrupt();
                            }
                            // try again ...
                            continue;
                        }

                        throw new SQLException(Util.rtp(
                                "No row exists, and one could not be inserted in table " + TABLE_PREFIX_SUBST + TABLE_LOCKS +
                                        " for lock named: " + lockName, getTablePrefix()));
                    }
                }

                return; // obtained lock, go
            } catch (SQLException sqle) {
                //Exception src =
                // (Exception)getThreadLocksObtainer().get(lockName);
                //if(src != null)
                //  src.printStackTrace();
                //else
                //  System.err.println("--- ***************** NO OBTAINER!");

                if(initCause == null) {
                    initCause = sqle;
                }

                if (getLog().isDebugEnabled()) {
                    getLog().debug(
                            "Lock '" + lockName + "' was not obtained by: " +
                                    Thread.currentThread().getName() + (count < 3 ? " - will try again." : ""));
                }

                if(count < 3) {
                    // pause a bit to give another thread some time to commit the insert of the new lock row
                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException ignore) {
                        Thread.currentThread().interrupt();
                    }
                    // try again ...
                    continue;
                }

                throw new LockException("Failure obtaining db row lock: "
                        + sqle.getMessage(), sqle);
            } finally {
                if (rs != null) {
                    try {
                        rs.close();
                    } catch (Exception ignore) {
                    }
                }
                if (ps != null) {
                    try {
                        ps.close();
                    } catch (Exception ignore) {
                    }
                }
            }
        } while(count < 4);

        throw new LockException("Failure obtaining db row lock, reached maximum number of attempts. Initial exception (if any) attached as root cause.", initCause);
    }
}
