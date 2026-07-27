FROM eclipse-temurin:21-jre-alpine

# Install bash for startup script
RUN apk add --no-cache bash

# Create app directory
WORKDIR /app

# Copy the application JAR
COPY build/libs/*.jar app.jar

# Copy the key extraction script
COPY deployment/extract-key.sh /app/extract-key.sh
RUN chmod +x /app/extract-key.sh

# Create directories for secrets and data
RUN mkdir -p /var/secrets /data

# Create startup script that extracts key and runs the app
RUN echo '#!/bin/bash' > /app/startup.sh && \
    echo 'set -e' >> /app/startup.sh && \
    echo 'echo "Starting Order Status Batch Application..."' >> /app/startup.sh && \
    echo '' >> /app/startup.sh && \
    echo '# Extract private key from vault-secrets.properties' >> /app/startup.sh && \
    echo 'if [ -f /var/secrets/vault-secrets.properties ]; then' >> /app/startup.sh && \
    echo '  echo "Extracting private key..."' >> /app/startup.sh && \
    echo '  /app/extract-key.sh' >> /app/startup.sh && \
    echo 'else' >> /app/startup.sh && \
    echo '  echo "WARNING: vault-secrets.properties not found"' >> /app/startup.sh && \
    echo 'fi' >> /app/startup.sh && \
    echo '' >> /app/startup.sh && \
    echo '# Start the Spring Boot application' >> /app/startup.sh && \
    echo 'exec java ${JAVA_OPTS} -jar /app/app.jar' >> /app/startup.sh && \
    chmod +x /app/startup.sh

# Expose health check port
EXPOSE 8080

# Use startup script as entrypoint
ENTRYPOINT ["/app/startup.sh"]

# Made with Bob
