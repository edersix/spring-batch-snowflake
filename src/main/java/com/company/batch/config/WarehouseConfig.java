package com.company.batch.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.snowflake.client.api.datasource.SnowflakeDataSource;
import net.snowflake.client.api.datasource.SnowflakeDataSourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Snowflake Warehouse Configuration
 *
 * Configures Snowflake datasource with Key Pair authentication using:
 * - HikariDataSource wrapping SnowflakeDataSource (datasource-proxy mode)
 * - setPrivateKeyFile(path, passphrase) — driver decrypts the key internally,
 *   no manual BouncyCastle decryption required
 * - Separate JPA configuration for Snowflake entities
 *
 * Best practices for OpenShift/Kubernetes:
 * - Private key and passphrase mounted by Vault sidecar
 * - No hardcoded credentials
 * - Fail fast on configuration errors
 */
@Configuration
@EnableJpaRepositories(
    basePackages = "com.company.batch.repository.snowflake",
    entityManagerFactoryRef = "snowflakeEntityManagerFactory",
    transactionManagerRef = "snowflakeTransactionManager"
)
public class WarehouseConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(WarehouseConfig.class);
    
    @Value("${spring.datasource.snowflake.url}")
    private String snowflakeUrl;

    @Value("${spring.datasource.snowflake.username}")
    private String snowflakeUsername;

    @Value("${spring.datasource.snowflake.private-key-path}")
    private String privateKeyPath;

    @Value("${spring.datasource.snowflake.passphrase}")
    private String passphrase;

    @Value("${spring.datasource.snowflake.pool.maximum-pool-size:10}")
    private int maximumPoolSize;

    @Value("${spring.datasource.snowflake.pool.minimum-idle:2}")
    private int minimumIdle;

    @Value("${spring.datasource.snowflake.pool.connection-timeout:30000}")
    private long connectionTimeout;

    @Value("${spring.datasource.snowflake.pool.idle-timeout:600000}")
    private long idleTimeout;

    @Value("${spring.datasource.snowflake.pool.max-lifetime:1800000}")
    private long maxLifetime;

    @Value("${batch.chunk-size:100}")
    private int jdbcBatchSize;

    /**
     * Configure Snowflake DataSource with Key Pair authentication wrapped in HikariCP.
     *
     * HikariCP runs in datasource-proxy mode: SnowflakeDataSource (4.x public API,
     * created via SnowflakeDataSourceFactory) is set as the underlying datasource so
     * the Snowflake JDBC driver handles authentication (setPrivateKeyFile decrypts
     * the key internally), while Hikari provides the connection pool on top.
     */
    @Bean(name = "snowflakeDataSource")
    public DataSource snowflakeDataSource() {
        logger.info("Configuring Snowflake HikariDataSource with Key Pair authentication");

        // 4.x: instantiate via factory — SnowflakeBasicDataSource is no longer public API
        SnowflakeDataSource snowflakeDs = SnowflakeDataSourceFactory.createDataSource();
        snowflakeDs.setUrl(snowflakeUrl);
        snowflakeDs.setUser(snowflakeUsername);
        // Driver decrypts the PKCS8 key internally using the passphrase
        snowflakeDs.setPrivateKeyFile(privateKeyPath, passphrase);

        HikariDataSource ds = new HikariDataSource(buildHikariConfig(snowflakeDs));
        logger.info("Snowflake HikariDataSource configured (pool size: {}/{})", minimumIdle, maximumPoolSize);
        return ds;
    }

    /**
     * Builds the HikariCP pool configuration wrapping the given Snowflake datasource.
     * Extracted to keep the @Bean method focused on wiring, not pool tuning.
     */
    private HikariConfig buildHikariConfig(SnowflakeDataSource snowflakeDs) {
        HikariConfig config = new HikariConfig();
        config.setDataSource(snowflakeDs);
        config.setPoolName("HikariPool-Snowflake");
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);
        // Validate connections before handing them out
        config.setConnectionTestQuery("SELECT 1");
        return config;
    }
    
    /**
     * Configure JPA EntityManagerFactory for Snowflake.
     * 
     * Separate entity manager allows:
     * - Different entity packages for different datasources
     * - Independent transaction management
     * - Snowflake-specific JPA settings
     */
    @Bean(name = "snowflakeEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean snowflakeEntityManagerFactory(
            @Qualifier("snowflakeDataSource") DataSource dataSource) {
        
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        
        // Scan for Snowflake entities
        em.setPackagesToScan("com.company.batch.entity.snowflake");
        
        // Use Hibernate as JPA provider
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        
        // Hibernate properties for Snowflake
        Properties properties = new Properties();

        // Don't auto-create schema (Snowflake tables must already exist)
        properties.setProperty("hibernate.hbm2ddl.auto", "none");

        // Let Hibernate auto-detect the dialect via DialectResolver.
        // Snowflake has no dedicated Hibernate 6 dialect; hard-coding
        // PostgreSQLDialect can produce subtly wrong SQL for Snowflake types.

        // Batch insert optimization — kept in sync with batch.chunk-size
        properties.setProperty("hibernate.jdbc.batch_size", String.valueOf(jdbcBatchSize));
        properties.setProperty("hibernate.order_inserts", "true");
        properties.setProperty("hibernate.order_updates", "true");

        // Show SQL for debugging (disable in production)
        properties.setProperty("hibernate.show_sql", "false");
        properties.setProperty("hibernate.format_sql", "false");
        
        em.setJpaProperties(properties);
        
        return em;
    }
    
    /**
     * Configure transaction manager for Snowflake datasource.
     * 
     * Separate transaction manager ensures:
     * - Independent transaction boundaries
     * - No interference with primary datasource transactions
     * - Proper rollback handling for Snowflake operations
     */
    @Bean(name = "snowflakeTransactionManager")
    public PlatformTransactionManager snowflakeTransactionManager(
            @Qualifier("snowflakeEntityManagerFactory") LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory.getObject());
        
        return transactionManager;
    }
}

// Made with Bob
