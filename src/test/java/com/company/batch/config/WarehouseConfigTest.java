package com.company.batch.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import net.snowflake.client.api.datasource.SnowflakeDataSource;
import net.snowflake.client.api.datasource.SnowflakeDataSourceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for WarehouseConfig.
 *
 * WarehouseConfig is instantiated directly (new) so Spring never tries to
 * initialise the @Bean methods during context startup — preventing any real
 * Snowflake connection attempt. @Value fields are injected via ReflectionTestUtils
 * using the same values defined in application-test.properties.
 * SnowflakeDataSourceFactory.createDataSource() is intercepted with MockedStatic
 * so no network call is made. Jenkins-pipeline safe — no Snowflake access needed.
 *
 * HikariCP eagerly validates the pool on construction by calling getConnection()
 * on the underlying DataSource. The mock must therefore stub getConnection() to
 * return a valid (mock) Connection; without this stub Hikari receives null and
 * throws PoolInitializationException before any assertion can run.
 */
@ExtendWith(MockitoExtension.class)
class WarehouseConfigTest {

    private WarehouseConfig config;

    @BeforeEach
    void setUp() {
        config = new WarehouseConfig();
        // Values mirror application-test.properties — kept in sync manually
        ReflectionTestUtils.setField(config, "snowflakeUrl",
                "jdbc:snowflake://dev.east.company.com/?warehouse=BT_WT&db=SSFF&schema=MY_SCM");
        ReflectionTestUtils.setField(config, "snowflakeUsername", "test_user");
        ReflectionTestUtils.setField(config, "privateKeyPath", "/secrets/snowflake_key.p8");
        ReflectionTestUtils.setField(config, "passphrase", "test-passphrase");
        ReflectionTestUtils.setField(config, "maximumPoolSize", 10);
        ReflectionTestUtils.setField(config, "minimumIdle", 2);
        ReflectionTestUtils.setField(config, "connectionTimeout", 30_000L);
        ReflectionTestUtils.setField(config, "idleTimeout", 600_000L);
        ReflectionTestUtils.setField(config, "maxLifetime", 1_800_000L);
    }

    /**
     * Stubs the mock SnowflakeDataSource so that HikariCP's eager pool
     * initialisation succeeds without a real Snowflake connection.
     *
     * HikariCP's init sequence (checkDriverSupport → checkValidationSupport):
     *  1. getConnection()           — must return a non-null Connection
     *  2. conn.getMetaData()        — used to read the database product name
     *  3. conn.createStatement()    — used to execute the connectionTestQuery ("SELECT 1")
     *  4. stmt.execute("SELECT 1")  — must not throw
     *  5. conn.getAutoCommit()      — used to detect the driver's default
     * All five are stubbed so the pool initialises without any real socket.
     */
    private static SnowflakeDataSource stubbedSnowflakeDs() throws SQLException {
        SnowflakeDataSource mockDs = mock(SnowflakeDataSource.class);
        Connection mockConn = mock(Connection.class);
        DatabaseMetaData mockMeta = mock(DatabaseMetaData.class);
        Statement mockStmt = mock(Statement.class);
        // Use lenient() so Mockito strict mode does not fail if HikariCP's init
        // path does not exercise every stub in every test variant.
        lenient().when(mockDs.getConnection()).thenReturn(mockConn);
        lenient().when(mockConn.getMetaData()).thenReturn(mockMeta);
        lenient().when(mockConn.createStatement()).thenReturn(mockStmt);
        lenient().when(mockConn.getAutoCommit()).thenReturn(true);
        lenient().when(mockMeta.getDatabaseProductName()).thenReturn("Snowflake");
        // stmt.execute() returns false by default in Mockito (boolean primitive) — no stub needed
        return mockDs;
    }

    // -------------------------------------------------------------------------
    // snowflakeDataSource
    // -------------------------------------------------------------------------

    @Test
    void snowflakeDataSource_returnsHikariDataSource() throws SQLException {
        SnowflakeDataSource mockSnowflakeDs = stubbedSnowflakeDs();

        try (MockedStatic<SnowflakeDataSourceFactory> factory =
                     mockStatic(SnowflakeDataSourceFactory.class)) {
            factory.when(SnowflakeDataSourceFactory::createDataSource).thenReturn(mockSnowflakeDs);

            DataSource ds = config.snowflakeDataSource();

            assertThat(ds).isInstanceOf(HikariDataSource.class);
            ((HikariDataSource) ds).close();
        }
    }

    @Test
    void snowflakeDataSource_appliesPoolName() throws SQLException {
        SnowflakeDataSource mockSnowflakeDs = stubbedSnowflakeDs();

        try (MockedStatic<SnowflakeDataSourceFactory> factory =
                     mockStatic(SnowflakeDataSourceFactory.class)) {
            factory.when(SnowflakeDataSourceFactory::createDataSource).thenReturn(mockSnowflakeDs);

            HikariDataSource ds = (HikariDataSource) config.snowflakeDataSource();

            assertThat(ds.getPoolName()).isEqualTo("HikariPool-Snowflake");
            ds.close();
        }
    }

    @Test
    void snowflakeDataSource_appliesPoolSizeSettings() throws SQLException {
        SnowflakeDataSource mockSnowflakeDs = stubbedSnowflakeDs();

        try (MockedStatic<SnowflakeDataSourceFactory> factory =
                     mockStatic(SnowflakeDataSourceFactory.class)) {
            factory.when(SnowflakeDataSourceFactory::createDataSource).thenReturn(mockSnowflakeDs);

            HikariDataSource ds = (HikariDataSource) config.snowflakeDataSource();

            assertThat(ds.getMaximumPoolSize()).isEqualTo(10);
            assertThat(ds.getMinimumIdle()).isEqualTo(2);
            ds.close();
        }
    }

    @Test
    void snowflakeDataSource_appliesTimeoutSettings() throws SQLException {
        SnowflakeDataSource mockSnowflakeDs = stubbedSnowflakeDs();

        try (MockedStatic<SnowflakeDataSourceFactory> factory =
                     mockStatic(SnowflakeDataSourceFactory.class)) {
            factory.when(SnowflakeDataSourceFactory::createDataSource).thenReturn(mockSnowflakeDs);

            HikariDataSource ds = (HikariDataSource) config.snowflakeDataSource();

            assertThat(ds.getConnectionTimeout()).isEqualTo(30_000L);
            assertThat(ds.getIdleTimeout()).isEqualTo(600_000L);
            assertThat(ds.getMaxLifetime()).isEqualTo(1_800_000L);
            ds.close();
        }
    }

    @Test
    void snowflakeDataSource_configuresSnowflakeDriverWithCredentials() throws SQLException {
        SnowflakeDataSource mockSnowflakeDs = stubbedSnowflakeDs();

        try (MockedStatic<SnowflakeDataSourceFactory> factory =
                     mockStatic(SnowflakeDataSourceFactory.class)) {
            factory.when(SnowflakeDataSourceFactory::createDataSource).thenReturn(mockSnowflakeDs);

            config.snowflakeDataSource();

            // Factory was called exactly once
            factory.verify(SnowflakeDataSourceFactory::createDataSource, times(1));

            // Credentials were passed to the Snowflake datasource
            verify(mockSnowflakeDs).setUrl(
                    "jdbc:snowflake://dev.east.company.com/?warehouse=BT_WT&db=SSFF&schema=MY_SCM");
            verify(mockSnowflakeDs).setUser("test_user");
            verify(mockSnowflakeDs).setPrivateKeyFile("/secrets/snowflake_key.p8", "test-passphrase");
        }
    }

    @Test
    void snowflakeDataSource_respectsCustomPoolSize() throws SQLException {
        // Override two pool fields to verify the bean respects injected values
        ReflectionTestUtils.setField(config, "maximumPoolSize", 20);
        ReflectionTestUtils.setField(config, "minimumIdle", 5);

        SnowflakeDataSource mockSnowflakeDs = stubbedSnowflakeDs();

        try (MockedStatic<SnowflakeDataSourceFactory> factory =
                     mockStatic(SnowflakeDataSourceFactory.class)) {
            factory.when(SnowflakeDataSourceFactory::createDataSource).thenReturn(mockSnowflakeDs);

            HikariDataSource ds = (HikariDataSource) config.snowflakeDataSource();

            assertThat(ds.getMaximumPoolSize()).isEqualTo(20);
            assertThat(ds.getMinimumIdle()).isEqualTo(5);
            ds.close();
        }
    }

    // -------------------------------------------------------------------------
    // snowflakeEntityManagerFactory
    // -------------------------------------------------------------------------

    @Test
    void snowflakeEntityManagerFactory_setsProvidedDataSource() {
        DataSource mockDs = mock(DataSource.class);

        LocalContainerEntityManagerFactoryBean em =
                config.snowflakeEntityManagerFactory(mockDs);

        assertThat(em.getDataSource()).isSameAs(mockDs);
    }

    // -------------------------------------------------------------------------
    // snowflakeTransactionManager
    // -------------------------------------------------------------------------

    @Test
    void snowflakeTransactionManager_returnsJpaTransactionManager() {
        LocalContainerEntityManagerFactoryBean emf =
                mock(LocalContainerEntityManagerFactoryBean.class);
        when(emf.getObject()).thenReturn(null);

        var tm = config.snowflakeTransactionManager(emf);

        assertThat(tm).isInstanceOf(JpaTransactionManager.class);
    }

    @Test
    void snowflakeTransactionManager_usesEntityManagerFactoryFromBean() {
        LocalContainerEntityManagerFactoryBean emf =
                mock(LocalContainerEntityManagerFactoryBean.class);
        when(emf.getObject()).thenReturn(null);

        JpaTransactionManager tm =
                (JpaTransactionManager) config.snowflakeTransactionManager(emf);

        verify(emf).getObject();
        assertThat(tm.getEntityManagerFactory()).isNull();
    }
}

// Made with Bob
