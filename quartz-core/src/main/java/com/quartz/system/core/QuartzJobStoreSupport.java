package com.quartz.system.core;

import org.quartz.SchedulerConfigException;
import org.quartz.impl.jdbcjobstore.*;
import org.quartz.spi.ClassLoadHelper;
import org.quartz.spi.SchedulerSignaler;
import org.quartz.utils.ConnectionProvider;
import org.quartz.utils.DBConnectionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * @packageName：com.quartz.system.core
 * @desrciption: 重写Quartz任务存储实现
 * @author: gaowei
 * @date： 2018-12-04 9:29
 * @history: (version) author date desc
 */
public class QuartzJobStoreSupport extends JobStoreCMT {

    public static String scheduleName;

    /**
     * Name used for the transactional ConnectionProvider for Quartz.
     * This provider will delegate to the local Spring-managed DataSource.
     * @see org.quartz.utils.DBConnectionManager#addConnectionProvider
     * @see SchedulerFactoryBean#setDataSource
     */
    public static final String TX_DATA_SOURCE_PREFIX = "springTxDataSource.";

    /**
     * Name used for the non-transactional ConnectionProvider for Quartz.
     * This provider will delegate to the local Spring-managed DataSource.
     * @see org.quartz.utils.DBConnectionManager#addConnectionProvider
     * @see SchedulerFactoryBean#setDataSource
     */
    public static final String NON_TX_DATA_SOURCE_PREFIX = "springNonTxDataSource.";


    private DataSource dataSource;


    @Override
    public void initialize(ClassLoadHelper loadHelper, SchedulerSignaler signaler) throws SchedulerConfigException {
        // Absolutely needs thread-bound DataSource to initialize.
        this.dataSource = QuartzSchedulerFactoryBean.getConfigTimeDataSource();
        if (this.dataSource == null) {
            throw new SchedulerConfigException(
                    "No local DataSource found for configuration - " +
                            "'dataSource' property must be set on SchedulerFactoryBean");
        }

        // Configure transactional connection settings for Quartz.
        setDataSource(TX_DATA_SOURCE_PREFIX + getInstanceName());
        setDontSetAutoCommitFalse(true);

        // Register transactional ConnectionProvider for Quartz.
        DBConnectionManager.getInstance().addConnectionProvider(
                TX_DATA_SOURCE_PREFIX + getInstanceName(),
                new ConnectionProvider() {
                    @Override
                    public Connection getConnection() throws SQLException {
                        // Return a transactional Connection, if any.
                        return DataSourceUtils.doGetConnection(dataSource);
                    }
                    @Override
                    public void shutdown() {
                        // Do nothing - a Spring-managed DataSource has its own lifecycle.
                    }
                }
        );

        // Non-transactional DataSource is optional: fall back to default
        // DataSource if not explicitly specified.
        DataSource nonTxDataSource = SchedulerFactoryBean.getConfigTimeNonTransactionalDataSource();
        final DataSource nonTxDataSourceToUse =
                (nonTxDataSource != null ? nonTxDataSource : this.dataSource);

        // Configure non-transactional connection settings for Quartz.
        setNonManagedTXDataSource(NON_TX_DATA_SOURCE_PREFIX + getInstanceName());

        // Register non-transactional ConnectionProvider for Quartz.
        DBConnectionManager.getInstance().addConnectionProvider(
                NON_TX_DATA_SOURCE_PREFIX + getInstanceName(),
                new ConnectionProvider() {
                    @Override
                    public Connection getConnection() throws SQLException {
                        // Always return a non-transactional Connection.
                        return nonTxDataSourceToUse.getConnection();
                    }
                    @Override
                    public void shutdown() {
                        // Do nothing - a Spring-managed DataSource has its own lifecycle.
                    }
                }
        );

        // No, if HSQL is the platform, we really don't want to use locks
        try {
            String productName = JdbcUtils.extractDatabaseMetaData(dataSource,
                    "getDatabaseProductName").toString();
            productName = JdbcUtils.commonDatabaseName(productName);
            if (productName != null
                    && productName.toLowerCase().contains("hsql")) {
                setUseDBLocks(false);
                setLockHandler(new SimpleSemaphore());
            }
        } catch (MetaDataAccessException e) {
            logWarnIfNonZero(1, "Could not detect database type.  Assuming locks can be taken.");
        }

        super.initialize(loadHelper, signaler);
        scheduleName = getInstanceName();
        if(getUseDBLocks()) {
            getLog().info("Using QuartzStdRowLockSemaphore(auto) access locking (synchronization).");
            setLockHandler(new QuartzStdRowLockSemaphore(getTablePrefix(),getInstanceName()));
        }
    }

    @Override
    protected void closeConnection(Connection con) {
        // Will work for transactional and non-transactional connections.
        DataSourceUtils.releaseConnection(con, this.dataSource);
    }
}
