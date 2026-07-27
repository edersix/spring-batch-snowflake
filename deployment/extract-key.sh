#!/bin/bash
# extract-key.sh
# Extracts the private key from vault-secrets.properties and creates a .p8 file

set -e

SECRETS_FILE="/var/secrets/vault-secrets.properties"
KEY_OUTPUT="/var/secrets/snowflake_key.p8"

echo "Extracting private key from ${SECRETS_FILE}..."

# Extract all lines starting with "key." and remove the "key.N=" prefix
# This reconstructs the multi-line private key file
sed -n 's/^key\.[0-9]*=//p' "${SECRETS_FILE}" > "${KEY_OUTPUT}"

# Verify the key file was created
if [ -f "${KEY_OUTPUT}" ]; then
    echo "Private key extracted successfully to ${KEY_OUTPUT}"
    
    # Set proper permissions (read-only for owner)
    chmod 400 "${KEY_OUTPUT}"
    
    # Verify it looks like a valid key file
    if head -n 1 "${KEY_OUTPUT}" | grep -q "BEGIN.*PRIVATE KEY"; then
        echo "Key file format validated"
    else
        echo "WARNING: Key file may not be in correct format"
        exit 1
    fi
else
    echo "ERROR: Failed to create key file"
    exit 1
fi

# Note: exporting PRIVATE_KEY_PATH here has no effect on the parent process.
# The application resolves the key path via spring.datasource.snowflake.private-key-path
# in application.yml, which defaults to /var/secrets/snowflake_key.p8 (written above).

# Made with Bob
