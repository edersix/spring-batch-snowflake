package com.company.batch.config;

import com.company.batch.entity.local.OrderStatus;
import com.company.batch.entity.snowflake.ReportDev;
import com.company.batch.model.OrderRecord;
import com.company.batch.repository.local.OrderStatusRepository;
import com.company.batch.repository.snowflake.ReportDevRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;

/**
 * Spring Batch Configuration
 * 
 * Defines the batch job with:
 * - Reader: Reads order records from text file
 * - Processor: Compares against local database and filters changed orders
 * - Writer: Persists changed orders to Snowflake
 */
@Configuration
public class BatchConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(BatchConfig.class);
    
    @Value("${batch.input.file-path}")
    private String inputFilePath;
    
    @Value("${batch.chunk-size:100}")
    private int chunkSize;
    
    private final OrderStatusRepository orderStatusRepository;
    private final ReportDevRepository reportDevRepository;
    
    public BatchConfig(OrderStatusRepository orderStatusRepository,
                      ReportDevRepository reportDevRepository) {
        this.orderStatusRepository = orderStatusRepository;
        this.reportDevRepository = reportDevRepository;
    }
    
    /**
     * Reader: Reads order records from text file
     * 
     * Expected file format (pipe-delimited):
     * ORDER_ID|STATUS
     * ORD001|SHIPPED
     * ORD002|DELIVERED
     */
    @Bean
    public FlatFileItemReader<OrderRecord> orderFileReader() {
        return new FlatFileItemReaderBuilder<OrderRecord>()
                .name("orderFileReader")
                .resource(new FileSystemResource(inputFilePath))
                .delimited()
                .delimiter("|")
                .names("orderId", "status")
                .linesToSkip(1) // Skip header
                .fieldSetMapper(new BeanWrapperFieldSetMapper<>() {{
                    setTargetType(OrderRecord.class);
                }})
                .build();
    }
    
    /**
     * Processor: Compares order status against local database
     * 
     * Logic:
     * 1. Look up order in local database
     * 2. If order doesn't exist, create new local record and return for Snowflake
     * 3. If order exists but status changed, update local record and return for Snowflake
     * 4. If order exists with same status, return null (skip writing to Snowflake)
     */
    @Bean
    public ItemProcessor<OrderRecord, ReportDev> orderStatusProcessor() {
        return item -> {
            logger.debug("Processing order: {}", item.getOrderId());
            
            try {
                Optional<OrderStatus> existingOrder = 
                        orderStatusRepository.findByOrderId(item.getOrderId());
                
                if (existingOrder.isEmpty()) {
                    // New order - save to local DB and prepare for Snowflake
                    logger.info("New order detected: {} with status {}", 
                            item.getOrderId(), item.getStatus());
                    
                    OrderStatus newOrder = new OrderStatus(item.getOrderId(), item.getStatus());
                    orderStatusRepository.save(newOrder);
                    
                    return new ReportDev(item.getOrderId(), item.getStatus(), null);
                    
                } else {
                    OrderStatus currentOrder = existingOrder.get();
                    
                    if (!currentOrder.getStatus().equals(item.getStatus())) {
                        // Status changed - update local DB and prepare for Snowflake
                        logger.info("Status change detected for order {}: {} -> {}", 
                                item.getOrderId(), currentOrder.getStatus(), item.getStatus());
                        
                        String previousStatus = currentOrder.getStatus();
                        currentOrder.setStatus(item.getStatus());
                        orderStatusRepository.save(currentOrder);
                        
                        return new ReportDev(item.getOrderId(), item.getStatus(), previousStatus);
                        
                    } else {
                        // No change - skip writing to Snowflake
                        logger.debug("No status change for order {}", item.getOrderId());
                        return null;
                    }
                }
                
            } catch (Exception e) {
                logger.error("Error processing order {}: {}", item.getOrderId(), e.getMessage(), e);
                throw e;
            }
        };
    }
    
    /**
     * Writer: Persists changed orders to Snowflake
     * 
     * Uses JPA repository to write to Snowflake datasource.
     * Batch operations are optimized via Hibernate batch settings.
     */
    @Bean
    public ItemWriter<ReportDev> snowflakeWriter() {
        return items -> {
            logger.info("Writing {} changed orders to Snowflake", items.size());
            
            try {
                reportDevRepository.saveAll(items);
                logger.info("Successfully wrote {} records to Snowflake", items.size());
                
            } catch (Exception e) {
                logger.error("Error writing to Snowflake: {}", e.getMessage(), e);
                throw e;
            }
        };
    }
    
    /**
     * Step: Defines the batch step with reader, processor, and writer
     */
    @Bean
    public Step orderProcessingStep(JobRepository jobRepository,
                                   PlatformTransactionManager transactionManager,
                                   ItemReader<OrderRecord> orderFileReader,
                                   ItemProcessor<OrderRecord, ReportDev> orderStatusProcessor,
                                   ItemWriter<ReportDev> snowflakeWriter) {
        
        return new StepBuilder("orderProcessingStep", jobRepository)
                .<OrderRecord, ReportDev>chunk(chunkSize, transactionManager)
                .reader(orderFileReader)
                .processor(orderStatusProcessor)
                .writer(snowflakeWriter)
                .faultTolerant()
                .skipLimit(10) // Skip up to 10 errors
                .skip(Exception.class)
                .build();
    }
    
    /**
     * Job: Defines the batch job
     */
    @Bean
    public Job orderStatusJob(JobRepository jobRepository,
                             Step orderProcessingStep) {
        
        return new JobBuilder("orderStatusJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(orderProcessingStep)
                .build();
    }
}

// Made with Bob
