# Testing Guide - S3 File Processor

Complete guide to test the Spring Boot application that streams large files from S3 to RabbitMQ.

## 📋 Table of Contents

1. [Prerequisites](#prerequisites)
2. [Local Testing Setup](#local-testing-setup)
3. [Unit Tests](#unit-tests)
4. [Integration Tests](#integration-tests)
5. [End-to-End Testing](#end-to-end-testing)
6. [Performance Testing](#performance-testing)
7. [Troubleshooting](#troubleshooting)

## Prerequisites

Before testing, ensure you have:

- ✅ Java 17+ installed
- ✅ Maven 3.6+ installed
- ✅ Docker and Docker Compose installed
- ✅ AWS CLI configured (for real S3 testing)
- ✅ curl or Postman for API testing

## Local Testing Setup

### Step 1: Start Required Services

Start RabbitMQ and LocalStack (local S3):

```bash
# Start services
docker-compose up -d

# Verify services are running
docker-compose ps

# Check RabbitMQ is ready
curl http://localhost:15672
# Default credentials: guest/guest

# Check LocalStack is ready
curl http://localhost:4566/_localstack/health
```

### Step 2: Create Local S3 Bucket

```bash
# Configure AWS CLI for LocalStack
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

# Create bucket in LocalStack
aws --endpoint-url=http://localhost:4566 s3 mb s3://test-bucket

# Verify bucket created
aws --endpoint-url=http://localhost:4566 s3 ls
```

### Step 3: Upload Test File to LocalStack

```bash
# Create a test file with 1000 lines
for i in {1..1000}; do echo "Line $i: This is test data for processing" >> test-file.txt; done

# Upload to LocalStack S3
aws --endpoint-url=http://localhost:4566 s3 cp test-file.txt s3://test-bucket/test-file.txt

# Verify upload
aws --endpoint-url=http://localhost:4566 s3 ls s3://test-bucket/
```

### Step 4: Configure Application for Local Testing

Create `src/main/resources/application-local.yml`:

```yaml
aws:
  s3:
    region: us-east-1
    bucket: test-bucket
    chunk-size: 1048576  # 1MB for testing
  credentials:
    access-key: test
    secret-key: test
    use-instance-profile: false

# Override S3 endpoint for LocalStack
cloud:
  aws:
    s3:
      endpoint: http://localhost:4566

spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

file:
  processing:
    batch-size: 10  # Smaller batch for testing
```

### Step 5: Build and Run Application

```bash
# Build the application
mvn clean package -DskipTests

# Run with local profile
java -jar target/s3-file-processor-1.0.0.jar --spring.profiles.active=local

# Or using Maven
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Unit Tests

### Run All Unit Tests

```bash
mvn test
```

### Run Specific Test Class

```bash
mvn test -Dtest=S3StreamingServiceTest
```

### Test Coverage Report

```bash
mvn clean test jacoco:report

# View report at: target/site/jacoco/index.html
```

## Integration Tests

### Run Integration Tests

```bash
# Start test containers
docker-compose up -d

# Run integration tests
mvn verify

# Or run specific integration test
mvn verify -Dit.test=FileProcessingIntegrationTest
```

## End-to-End Testing

### Test 1: Process Small File (Basic Test)

```bash
# 1. Create small test file
echo -e "line1\nline2\nline3" > small-test.txt

# 2. Upload to S3 (LocalStack)
aws --endpoint-url=http://localhost:4566 s3 cp small-test.txt s3://test-bucket/small-test.txt

# 3. Trigger processing via API
curl -X POST http://localhost:8080/api/v1/files/process \
  -H "Content-Type: application/json" \
  -d '{"s3Key": "small-test.txt"}'

# 4. Check RabbitMQ Management UI
# Open: http://localhost:15672
# Login: guest/guest
# Navigate to Queues → processed-files-queue
# You should see 3 messages
```

### Test 2: Process Large File (Memory Test)

```bash
# 1. Create large test file (100MB, ~1 million lines)
for i in {1..1000000}; do echo "Line $i: $(date +%s%N) - Large file test data with some content to make it realistic" >> large-test.txt; done

# 2. Check file size
ls -lh large-test.txt

# 3. Upload to S3
aws --endpoint-url=http://localhost:4566 s3 cp large-test.txt s3://test-bucket/large-test.txt

# 4. Monitor memory before processing
jps -l  # Get Java process ID
jstat -gc <PID> 1000  # Monitor GC every second

# 5. Trigger processing
curl -X POST http://localhost:8080/api/v1/files/process \
  -H "Content-Type: application/json" \
  -d '{"s3Key": "large-test.txt"}'

# 6. Monitor application logs
tail -f logs/s3-file-processor.log

# 7. Verify memory stays constant (should not grow with file size)
```

### Test 3: Process CSV File with Custom Transformation

```bash
# 1. Create CSV test file
cat > test-data.csv << EOF
id,name,email,age
1,John Doe,john@example.com,30
2,Jane Smith,jane@example.com,25
3,Bob Johnson,bob@example.com,35
EOF

# 2. Upload to S3
aws --endpoint-url=http://localhost:4566 s3 cp test-data.csv s3://test-bucket/test-data.csv

# 3. Process file
curl -X POST http://localhost:8080/api/v1/files/process \
  -H "Content-Type: application/json" \
  -d '{"s3Key": "test-data.csv"}'

# 4. Consume messages from RabbitMQ
# Install rabbitmq-perf-test or use Python script below
```

### Test 4: Verify RabbitMQ Messages

Create `consume_messages.py`:

```python
#!/usr/bin/env python3
import pika
import json

# Connect to RabbitMQ
connection = pika.BlockingConnection(
    pika.ConnectionParameters('localhost')
)
channel = connection.channel()

# Declare queue
channel.queue_declare(queue='processed-files-queue', durable=True)

print('Waiting for messages. Press CTRL+C to exit.')

def callback(ch, method, properties, body):
    message = json.loads(body)
    print(f"Received: {message}")
    ch.basic_ack(delivery_tag=method.delivery_tag)

channel.basic_consume(
    queue='processed-files-queue',
    on_message_callback=callback
)

try:
    channel.start_consuming()
except KeyboardInterrupt:
    channel.stop_consuming()
    connection.close()
```

Run consumer:

```bash
python3 consume_messages.py
```

## Performance Testing

### Test Memory Efficiency

```bash
# 1. Start application with memory monitoring
java -Xmx512m -Xms256m \
  -XX:+PrintGCDetails \
  -XX:+PrintGCDateStamps \
  -Xloggc:gc.log \
  -jar target/s3-file-processor-1.0.0.jar

# 2. Create very large file (1GB)
dd if=/dev/urandom bs=1M count=1024 | base64 > huge-file.txt

# 3. Upload and process
aws --endpoint-url=http://localhost:4566 s3 cp huge-file.txt s3://test-bucket/huge-file.txt

curl -X POST http://localhost:8080/api/v1/files/process \
  -H "Content-Type: application/json" \
  -d '{"s3Key": "huge-file.txt"}'

# 4. Analyze GC log
# Memory should stay within 512MB limit
```

### Load Testing with Apache Bench

```bash
# Create test payload
cat > payload.json << EOF
{"s3Key": "test-file.txt"}
EOF

# Run load test (100 requests, 10 concurrent)
ab -n 100 -c 10 -p payload.json -T application/json \
  http://localhost:8080/api/v1/files/process
```

### Throughput Testing

```bash
# Monitor processing speed
curl -X POST http://localhost:8080/api/v1/files/process \
  -H "Content-Type: application/json" \
  -d '{"s3Key": "large-test.txt"}' \
  -w "\nTime: %{time_total}s\n"
```

## Testing with Real AWS S3

### Setup

```bash
# 1. Configure AWS credentials
aws configure

# 2. Create S3 bucket
aws s3 mb s3://your-real-bucket-name

# 3. Upload test file
aws s3 cp test-file.txt s3://your-real-bucket-name/

# 4. Update application.yml
# Change bucket name to your-real-bucket-name
# Remove LocalStack endpoint configuration

# 5. Run application
mvn spring-boot:run

# 6. Process file
curl -X POST http://localhost:8080/api/v1/files/process \
  -H "Content-Type: application/json" \
  -d '{"s3Key": "test-file.txt"}'
```

## Health Checks

### Application Health

```bash
# Check application health
curl http://localhost:8080/actuator/health

# Expected response:
# {"status":"UP"}
```

### RabbitMQ Health

```bash
# Check RabbitMQ
curl -u guest:guest http://localhost:15672/api/overview

# Check queue status
curl -u guest:guest http://localhost:15672/api/queues/%2F/processed-files-queue
```

### Metrics

```bash
# View all metrics
curl http://localhost:8080/actuator/metrics

# View specific metric
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# Prometheus metrics
curl http://localhost:8080/actuator/prometheus
```

## Troubleshooting

### Issue: OutOfMemoryError

**Solution:**
```bash
# Reduce batch size in application.yml
file.processing.batch-size: 50

# Reduce chunk size
aws.s3.chunk-size: 5242880  # 5MB

# Increase heap size
java -Xmx1g -jar target/s3-file-processor-1.0.0.jar
```

### Issue: Connection Timeout to S3

**Solution:**
```bash
# Check LocalStack is running
docker-compose ps

# Restart LocalStack
docker-compose restart localstack

# Verify endpoint
curl http://localhost:4566/_localstack/health
```

### Issue: RabbitMQ Connection Refused

**Solution:**
```bash
# Check RabbitMQ is running
docker-compose ps rabbitmq

# Check logs
docker-compose logs rabbitmq

# Restart RabbitMQ
docker-compose restart rabbitmq
```

### Issue: Messages Not Appearing in Queue

**Solution:**
```bash
# Check exchange and binding
curl -u guest:guest http://localhost:15672/api/exchanges/%2F/file-processing-exchange

# Check queue
curl -u guest:guest http://localhost:15672/api/queues/%2F/processed-files-queue

# Check application logs
tail -f logs/s3-file-processor.log
```

## Cleanup

```bash
# Stop services
docker-compose down

# Remove volumes (clean slate)
docker-compose down -v

# Remove test files
rm -f test-file.txt large-test.txt huge-file.txt test-data.csv small-test.txt

# Clean Maven build
mvn clean
```

## Automated Test Script

Create `run-tests.sh`:

```bash
#!/bin/bash
set -e

echo "🚀 Starting automated tests..."

# Start services
echo "📦 Starting Docker services..."
docker-compose up -d
sleep 10

# Create test bucket
echo "🪣 Creating S3 bucket..."
aws --endpoint-url=http://localhost:4566 s3 mb s3://test-bucket 2>/dev/null || true

# Create test file
echo "📝 Creating test file..."
for i in {1..100}; do echo "Line $i: Test data" >> test-file.txt; done

# Upload to S3
echo "⬆️  Uploading to S3..."
aws --endpoint-url=http://localhost:4566 s3 cp test-file.txt s3://test-bucket/

# Build application
echo "🔨 Building application..."
mvn clean package -DskipTests

# Start application in background
echo "▶️  Starting application..."
java -jar target/s3-file-processor-1.0.0.jar --spring.profiles.active=local &
APP_PID=$!
sleep 15

# Wait for application to be ready
echo "⏳ Waiting for application..."
until curl -s http://localhost:8080/actuator/health > /dev/null; do
    sleep 2
done

# Run test
echo "🧪 Running test..."
RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/files/process \
  -H "Content-Type: application/json" \
  -d '{"s3Key": "test-file.txt"}')

echo "Response: $RESPONSE"

# Check RabbitMQ
echo "📬 Checking RabbitMQ..."
QUEUE_INFO=$(curl -s -u guest:guest http://localhost:15672/api/queues/%2F/processed-files-queue)
MESSAGE_COUNT=$(echo $QUEUE_INFO | grep -o '"messages":[0-9]*' | grep -o '[0-9]*')

echo "Messages in queue: $MESSAGE_COUNT"

# Cleanup
echo "🧹 Cleaning up..."
kill $APP_PID
docker-compose down
rm -f test-file.txt

if [ "$MESSAGE_COUNT" -gt 0 ]; then
    echo "✅ Tests passed! $MESSAGE_COUNT messages processed."
    exit 0
else
    echo "❌ Tests failed! No messages in queue."
    exit 1
fi
```

Make it executable and run:

```bash
chmod +x run-tests.sh
./run-tests.sh
```

## Summary

You now have multiple ways to test:

1. ✅ **Unit Tests**: `mvn test`
2. ✅ **Integration Tests**: `mvn verify`
3. ✅ **Local E2E**: Using LocalStack + RabbitMQ
4. ✅ **Real AWS**: Using actual S3 bucket
5. ✅ **Performance**: Memory and throughput testing
6. ✅ **Automated**: Run complete test suite with script

For questions, check the logs at `logs/s3-file-processor.log` or application console output.