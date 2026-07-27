package com.company.batch.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Batch Job Scheduler
 * 
 * Triggers the batch job execution on a schedule.
 * 
 * For OpenShift CronJob deployment:
 * - This scheduler can be disabled by setting spring.batch.job.enabled=false
 * - The CronJob will start the application, which runs the job once and exits
 * - Alternatively, keep this enabled for testing in non-production environments
 * 
 * Cron expression examples:
 * - "0 0 2 * * ?"  - Daily at 2 AM
 * - "0 0/30 * * * ?" - Every 30 minutes
 * - "0 0 0/6 * * ?"  - Every 6 hours
 */
@Component
public class BatchScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(BatchScheduler.class);
    
    @Autowired
    private JobLauncher jobLauncher;
    
    @Autowired
    private Job orderStatusJob;
    
    /**
     * Scheduled job execution
     * 
     * Runs daily at 2 AM
     * Adjust cron expression as needed
     */
    @Scheduled(cron = "${batch.schedule.cron:0 0 2 * * ?}")
    public void runBatchJob() {
        logger.info("Starting scheduled batch job execution");
        
        try {
            // Create unique job parameters to allow re-running
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();
            
            jobLauncher.run(orderStatusJob, jobParameters);
            
            logger.info("Batch job completed successfully");
            
        } catch (Exception e) {
            logger.error("Batch job execution failed", e);
            // In production, you might want to send alerts here
            throw new RuntimeException("Batch job failed", e);
        }
    }
    
    /**
     * Manual trigger method for testing
     * Can be called via REST endpoint or command line
     */
    public void triggerManually() {
        logger.info("Manual batch job trigger requested");
        runBatchJob();
    }
}

// Made with Bob
