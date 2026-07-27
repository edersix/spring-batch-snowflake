# Order Status Batch - Spring Boot + Snowflake Integration

Spring Boot application that processes order status files and syncs changed orders to Snowflake using Key Pair authentication. Designed to run as a CronJob in OpenShift.

## Features

- **Java 21** with Spring Boot 3.1.5
- **Spring Batch 5** for file processing
- **Multi-datasource**: Local H2 + Snowflake
- **Snowflake Key Pair Authentication** (PKCS8 encrypted, decrypted by JDBC driver)
- **JPA/Hibernate** for database operations
- **Vault Secret Manager** integration via mounted properties file
- **OpenShift/Kubernetes** ready with CronJob support

## Architecture

The application follows this flow:

1. **Reader**: Reads order records from pipe-delimited text file
2. **Processor**: Compares each order against local database
   - New orders → Save locally + forward to writer
   - Changed status → Update locally + forward to writer
   - Unchanged → Skip (null return)
3. **Writer**: Persists only changed orders to Snowflake using JPA

## Project Structure

```
spring-batch-snowflake/
├── src/main/java/com/company/batch/
│   ├── OrderStatusBatchApplication.java    # Main application
│   ├── config/
│   │   ├── BatchConfig.java                # Batch job (Reader/Processor/Writer)
│   │   ├── PrimaryDataSourceConfig.java    # Local datasource config
│   │   └── WarehouseConfig.java            # Snowflake datasource + Key Pair auth
│   ├── entity/
│   │   ├── local/OrderStatus.java          # Local comparison entity
│   │   └── snowflake/ReportDev.java        # Snowflake warehouse entity
│   ├── repository/
│   │   ├── local/OrderStatusRepository.java
│   │   └── snowflake/ReportDevRepository.java
│   ├── model/OrderRecord.java              # DTO for file data
│   ├── scheduler/BatchScheduler.java       # Scheduled job trigger
│   └── util/PrivateKeyLoader.java          # PKCS8 key decryption
├── src/main/resources/
│   └── application.yml                     # Configuration
├── deployment/
│   ├── Dockerfile                          # Container image
│   ├── extract-key.sh                      # Key extraction script
│   ├── cronjob.yaml                        # OpenShift CronJob
│   └── vault-secrets-example.properties    # Secret manager format
├── data/
│   └── orders-sample.txt                   # Sample input file
└── build.gradle                            # Dependencies
```

## Configuration

### Vault Secret Manager Integration

The application loads secrets from `/var/secrets/vault-secrets.properties` mounted by your secret manager.

**Example vault-secrets.properties format:**

```properties
snowflake.url=jdbc:snowflake://dev.east.company.com/?warehouse=BT_WT&db=SSFF&schema=MY_SCM
snowflake.username=myuser
snowflake.passphrase=your-encrypted-passphrase

# Private key stored inline (extracted by startup script)
key.1=-----BEGIN ENCRYPTED PRIVATE KEY-----
key.2=MIIFHDBOBgkqhkiG9w0BBQ0wQTApBgkqhkiG9w0BBQwwHAQI...
key.3=...
key.N=-----END ENCRYPTED PRIVATE KEY-----

snowflake.private-key-path=/var/secrets/snowflake_key.p8
batch.input.file-path=/data/orders.txt
```

The startup script extracts the key using:
```bash
sed -n 's/^key\.[0-9]*=//p' /var/secrets/vault-secrets.properties > /var/secrets/snowflake_key.p8
```

### Snowflake Setup

1. **Generate Key Pair:**
```bash
# Generate encrypted private key
openssl genrsa 2048 | openssl pkcs8 -topk8 -v2 des3 -inform PEM -out snowflake_key.p8

# Extract public key
openssl rsa -in snowflake_key.p8 -pubout -out snowflake_key.pub
```

2. **Configure Snowflake User:**
```sql
-- Remove password authentication
ALTER USER myuser SET PASSWORD = NULL;

-- Set RSA public key (remove header/footer and newlines)
ALTER USER myuser SET RSA_PUBLIC_KEY='MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...';

-- Grant necessary privileges
GRANT USAGE ON WAREHOUSE BT_WT TO USER myuser;
GRANT USAGE ON DATABASE SSFF TO USER myuser;
GRANT USAGE ON SCHEMA MY_SCM TO USER myuser;
GRANT INSERT, SELECT ON TABLE REPORT_DEV TO USER myuser;
```

3. **Create Snowflake Table:**
```sql
CREATE TABLE SSFF.MY_SCM.REPORT_DEV (
  ID NUMBER AUTOINCREMENT PRIMARY KEY,
  ORDER_ID VARCHAR(50) NOT NULL,
  ORDER_STATUS VARCHAR(20) NOT NULL,
  PREVIOUS_STATUS VARCHAR(20),
  UPDATED_AT TIMESTAMP_NTZ,
  PROCESSED_AT TIMESTAMP_NTZ DEFAULT CURRENT_TIMESTAMP()
);
```

## Building

```bash
# Build JAR
./gradlew clean build

# Build Docker image
docker build -f deployment/Dockerfile -t order-status-batch:latest .
```

## Running Locally

```bash
# Set environment variables
export PRIVATE_KEY_PATH=/path/to/snowflake_key.p8
export PASSPHRASE=your-passphrase

# Run application
./gradlew bootRun
```

## OpenShift Deployment

### 1. Create Secret with vault-secrets.properties

```bash
kubectl create secret generic vault-secrets \
  --from-file=vault-secrets.properties=/path/to/vault-secrets.properties \
  -n your-namespace
```

### 2. Deploy CronJob

```bash
kubectl apply -f deployment/cronjob.yaml -n your-namespace
```

### 3. Manual Job Trigger (for testing)

```bash
kubectl create job --from=cronjob/order-status-batch manual-run-1 -n your-namespace
```

## Input File Format

Pipe-delimited text file with header:

```
ORDER_ID|STATUS
ORD001|SHIPPED
ORD002|DELIVERED
ORD003|PENDING
```

## Key Components Explained

### WarehouseConfig
- Configures Snowflake datasource with `SnowflakeBasicDataSource`
- Calls `setPrivateKeyFile(path, passphrase)` — the Snowflake JDBC driver decrypts the key internally, no BouncyCastle required
- Separate JPA EntityManager for Snowflake entities

### BatchConfig
- **Reader**: `FlatFileItemReader` for pipe-delimited files
- **Processor**: Compares against local DB, returns only changed orders
- **Writer**: JPA batch insert to Snowflake (chunk size: 100)
- Fault-tolerant with skip limit

### Multi-Datasource
- **Primary**: H2 for Spring Batch metadata and local comparison
- **Snowflake**: Warehouse for changed order persistence
- Separate transaction managers for isolation

## Monitoring

Health check endpoint: `http://localhost:8080/actuator/health`

Logs include:
- Key extraction status
- Batch job execution metrics
- Order processing details
- Snowflake write operations

## Troubleshooting

**Key decryption fails:**
- Verify passphrase is correct
- Check key file format (must start with `-----BEGIN ENCRYPTED PRIVATE KEY-----`)
- Ensure BouncyCastle provider is loaded

**Snowflake connection fails:**
- Verify public key is configured in Snowflake user
- Check network connectivity to Snowflake
- Validate JDBC URL format

**No records written to Snowflake:**
- Check if orders have actually changed status
- Review processor logs for comparison results
- Verify Snowflake table permissions

## License

Proprietary - Company Internal Use Only