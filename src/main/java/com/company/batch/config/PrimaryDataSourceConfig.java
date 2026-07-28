package com.company.batch.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Primary DataSource Configuration
 * 
 * Configures the primary (local) datasource used for:
 * - Spring Batch metadata tables (BATCH_JOB_EXECUTION, etc.)
 * - Local comparison data
 * - Application state management
 * 
 * This is marked as @Primary to be the default datasource for:
 * - Spring Batch infrastructure
 * - Any repositories not explicitly configured for Snowflake
 */
@Configuration
@EnableJpaRepositories(
    basePackages = "com.company.batch.repository.local",
    entityManagerFactoryRef = "primaryEntityManagerFactory",
    transactionManagerRef = "primaryTransactionManager"
)
public class PrimaryDataSourceConfig {

    @org.springframework.beans.factory.annotation.Value("${spring.jpa.hibernate.ddl-auto:none}")
    private String ddlAuto;
    
    /**
     * Primary datasource properties from application.yml
     * Prefix: spring.datasource.primary
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.primary")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }
    
    /**
     * Primary datasource bean
     * Used for Spring Batch metadata and local entities
     */
    @Bean
    @Primary
    public DataSource primaryDataSource() {
        return primaryDataSourceProperties()
                .initializeDataSourceBuilder()
                .build();
    }
    
    /**
     * Primary EntityManagerFactory
     * Manages local entities and Spring Batch metadata
     */
    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean primaryEntityManagerFactory(
            @Qualifier("primaryDataSource") DataSource dataSource) {
        
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        
        // Scan for local entities only — Spring Batch 5 uses JDBC (not JPA) for its metadata
        em.setPackagesToScan("com.company.batch.entity.local");
        
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        
        Properties properties = new Properties();
        // Spring Batch schema is initialised by spring.batch.jdbc.initialize-schema=always (JDBC)
        properties.setProperty("hibernate.hbm2ddl.auto", ddlAuto);
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        properties.setProperty("hibernate.show_sql", "false");
        
        em.setJpaProperties(properties);
        
        return em;
    }
    
    /**
     * Primary transaction manager
     * Handles transactions for local datasource and Spring Batch
     */
    @Bean
    @Primary
    public PlatformTransactionManager primaryTransactionManager(
            @Qualifier("primaryEntityManagerFactory") LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory.getObject());
        
        return transactionManager;
    }
}

// Made with Bob
