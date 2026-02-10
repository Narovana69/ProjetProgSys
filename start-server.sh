#!/bin/bash
# NEXO Server Launch Script

echo "====================================="
echo "   Starting NEXO Server"
echo "====================================="
echo ""

cd "$(dirname "$0")"
mvn exec:java -Dexec.mainClass="com.reseau.server.Server"
