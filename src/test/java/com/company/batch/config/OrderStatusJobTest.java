package com.company.batch.config;

import com.company.batch.OrderStatusBatchApplication;
import com.company.batch.entity.local.OrderStatus;
import com.company.batch.entity.snowflake.ReportDev;
import com.company.batch.repository.local.OrderStatusRepository;
import com.company.batch.repository.snowflake.ReportDevRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Integration tests for the orderStatusJob end-to-end pipeline.
 *
 * Two @Nested classes each carry a dedicated @SpringBootTest context so the
 * batch.input.file-path @Value (resolved at bean-creation time) can point to a
 * different fixture file per scenario — no runtime property hacks required.
 *
 * Both contexts explicitly import SnowflakeStubConfig (defined below the outer
 * class) which replaces WarehouseConfig's Snowflake stack with H2-backed stubs,
 * preventing any real Snowflake network connection.
 *
 * @MockBean on ReportDevRepository captures every call to saveAll so assertions
 * can be made without touching a real database.
 *
 * Fixture files (src/test/resources/testdata/)
 *   orders-happy.txt       — 3 orders: new / unchanged / changed status
 *   orders-writer-fail.txt — 15 orders: all new; writer throws on every saveAll
 */
class OrderStatusJobTest {

    // =========================================================================
    // Test 1 — Happy path
    //
    // orders-happy.txt
    //   ORD001|SHIPPED    new order             → creates local record, forwarded to writer
    //   ORD002|PENDING    pre-seeded PENDING     → status unchanged, processor returns null
    //   ORD003|DELIVERED  pre-seeded SHIPPED     → status changed, forwarded to writer
    //
    // Expected:
    //   • Job status = COMPLETED
    //   • saveAll called once carrying exactly 2 ReportDev records
    //   • ORD001: orderStatus=SHIPPED,    previousStatus=null  (new order)
    //   • ORD003: orderStatus=DELIVERED,  previousStatus=SHIPPED
    //   • Local DB holds final statuses for all three orders
    // =========================================================================
    @Nested
    @SpringBatchTest
    @SpringBootTest(
            classes = {OrderStatusBatchApplication.class, SnowflakeStubConfig.class},
            properties = "spring.main.allow-bean-definition-overriding=true"
    )
    @TestPropertySource(properties = {
            "spring.config.import=",
            "spring.datasource.snowflake.url=jdbc:snowflake://test",
            "spring.datasource.snowflake.username=test",
            "spring.datasource.snowflake.private-key-path=/dev/null",
            "spring.datasource.snowflake.passphrase=test",
            "spring.datasource.primary.url=jdbc:h2:mem:happydb;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS MY_SCM",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "batch.input.file-path=src/test/resources/testdata/orders-happy.txt"
    })
    class HappyPath {

        @Autowired private JobLauncherTestUtils jobLauncherTestUtils;
        @Autowired private Job orderStatusJob;
        @Autowired private OrderStatusRepository orderStatusRepository;
        @MockBean  private ReportDevRepository reportDevRepository;

        @BeforeEach
        void setUp() {
            orderStatusRepository.deleteAll();
            jobLauncherTestUtils.setJob(orderStatusJob);
        }

        @Test
        void completedWithOnlyChangedOrdersWrittenToSnowflake() throws Exception {
            // Seed local DB: ORD002 (unchanged) and ORD003 (about to change)
            orderStatusRepository.save(new OrderStatus("ORD002", "PENDING"));
            orderStatusRepository.save(new OrderStatus("ORD003", "SHIPPED"));

            JobExecution execution = jobLauncherTestUtils.launchJob(
                    new JobParametersBuilder()
                            .addLong("run.id", System.currentTimeMillis())
                            .toJobParameters());

            // --- job outcome ---
            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

            // --- Snowflake write assertions ---
            // Spring Batch 5 passes a Chunk<ReportDev> to saveAll; capture as Iterable
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Iterable<ReportDev>> captor =
                    ArgumentCaptor.forClass(Iterable.class);
            verify(reportDevRepository, times(1)).saveAll(captor.capture());

            List<ReportDev> written = new ArrayList<>();
            captor.getValue().forEach(written::add);
            assertThat(written).hasSize(2);

            ReportDev ord001 = written.stream()
                    .filter(r -> "ORD001".equals(r.getOrderId()))
                    .findFirst().orElseThrow();
            assertThat(ord001.getOrderStatus()).isEqualTo("SHIPPED");
            assertThat(ord001.getPreviousStatus()).isNull();  // new order — no previous

            ReportDev ord003 = written.stream()
                    .filter(r -> "ORD003".equals(r.getOrderId()))
                    .findFirst().orElseThrow();
            assertThat(ord003.getOrderStatus()).isEqualTo("DELIVERED");
            assertThat(ord003.getPreviousStatus()).isEqualTo("SHIPPED");

            // --- local DB state ---
            assertThat(orderStatusRepository.findByOrderId("ORD001"))
                    .map(OrderStatus::getStatus).hasValue("SHIPPED");
            assertThat(orderStatusRepository.findByOrderId("ORD002"))
                    .map(OrderStatus::getStatus).hasValue("PENDING");
            assertThat(orderStatusRepository.findByOrderId("ORD003"))
                    .map(OrderStatus::getStatus).hasValue("DELIVERED");
        }
    }

    // =========================================================================
    // Test 2 — Writer failure / skip limit exceeded
    //
    // orders-writer-fail.txt  15 new orders (ERR001–ERR015)
    // saveAll throws RuntimeException on every invocation.
    //
    // Spring Batch fault-tolerant replay:
    //   chunk attempted → writer throws → rolled back → replayed one-by-one
    //   every individual write failure counts as 1 skip
    //   skipLimit=10 → after 11 failures the step marks itself (and the job) FAILED
    //
    // Expected:
    //   • Job status = FAILED
    //   • saveAll was invoked at least once (before budget exhausted)
    // =========================================================================
    @Nested
    @SpringBatchTest
    @SpringBootTest(
            classes = {OrderStatusBatchApplication.class, SnowflakeStubConfig.class},
            properties = "spring.main.allow-bean-definition-overriding=true"
    )
    @TestPropertySource(properties = {
            "spring.config.import=",
            "spring.datasource.snowflake.url=jdbc:snowflake://test",
            "spring.datasource.snowflake.username=test",
            "spring.datasource.snowflake.private-key-path=/dev/null",
            "spring.datasource.snowflake.passphrase=test",
            "spring.datasource.primary.url=jdbc:h2:mem:faildb;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS MY_SCM",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "batch.input.file-path=src/test/resources/testdata/orders-writer-fail.txt"
    })
    class WriterFailure {

        @Autowired private JobLauncherTestUtils jobLauncherTestUtils;
        @Autowired private Job orderStatusJob;
        @Autowired private OrderStatusRepository orderStatusRepository;
        @MockBean  private ReportDevRepository reportDevRepository;

        @BeforeEach
        void setUp() {
            orderStatusRepository.deleteAll();
            jobLauncherTestUtils.setJob(orderStatusJob);
        }

        @Test
        void jobFailsWhenSkipLimitExceeded() throws Exception {
            // Writer always throws — simulates a persistent Snowflake outage
            doThrow(new RuntimeException("Snowflake connection refused"))
                    .when(reportDevRepository).saveAll(any());

            JobExecution execution = jobLauncherTestUtils.launchJob(
                    new JobParametersBuilder()
                            .addLong("run.id", System.currentTimeMillis())
                            .toJobParameters());

            // Job must fail once the skip budget is exhausted
            assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);

            // Writer was called at least during the initial chunk attempt
            verify(reportDevRepository, atLeastOnce()).saveAll(any());
        }
    }
}

/**
 * Replaces WarehouseConfig's Snowflake infrastructure beans with H2-backed stubs.
 *
 * Explicitly imported by each @Nested @SpringBootTest context via
 * @SpringBootTest(classes = {OrderStatusBatchApplication.class, SnowflakeStubConfig.class}).
 * This guarantees the stubs are loaded in every nested context, not just the outer one.
 *
 * All three beans (snowflakeDataSource, snowflakeEntityManagerFactory,
 * snowflakeTransactionManager) are overridden with equivalents that point to the
 * same in-memory H2 instance used by the primary datasource, so no Snowflake
 * connection pool is ever created.
 */
@TestConfiguration
class SnowflakeStubConfig {

    @Bean(name = "snowflakeDataSource")
    DataSource snowflakeDataSource(
            @Qualifier("primaryDataSource") DataSource primary) {
        return primary;  // reuse H2 — no Snowflake connection
    }

    @Bean(name = "snowflakeEntityManagerFactory")
    LocalContainerEntityManagerFactoryBean snowflakeEntityManagerFactory(
            @Qualifier("snowflakeDataSource") DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em =
                new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.company.batch.entity.snowflake");
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Properties props = new Properties();
        props.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        props.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        em.setJpaProperties(props);
        return em;
    }

    @Bean(name = "snowflakeTransactionManager")
    PlatformTransactionManager snowflakeTransactionManager(
            @Qualifier("snowflakeEntityManagerFactory")
            LocalContainerEntityManagerFactoryBean emf) {
        JpaTransactionManager tm = new JpaTransactionManager();
        tm.setEntityManagerFactory(emf.getObject());
        return tm;
    }
}
