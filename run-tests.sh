#!/bin/bash
set -e

echo "🚀 Starting S3 File Processor Tests..."
echo "======================================"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
AWS_ENDPOINT="http://localhost:4566"
BUCKET_NAME="test-bucket"
TEST_FILE="test-file.txt"
RABBITMQ_URL="http://localhost:15672"

# Function to print colored output
print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ️  $1${NC}"
}

# Function to check if service is ready
wait_for_service() {
    local url=$1
    local service_name=$2
    local max_attempts=30
    local attempt=0

    print_info "Waiting for $service_name to be ready..."
    
    while [ $attempt -lt $max_attempts ]; do
        if curl -s "$url" > /dev/null 2>&1; then
            print_success "$service_name is ready!"
            return 0
        fi
        attempt=$((attempt + 1))
        sleep 2
    done
    
    print_error "$service_name failed to start"
    return 1
}

# Cleanup function
cleanup() {
    print_info "Cleaning up..."
    
    # Kill application if running
    if [ ! -z "$APP_PID" ]; then
        kill $APP_PID 2>/dev/null || true
    fi
    
    # Remove test files
    rm -f $TEST_FILE large-test.txt
    
    print_success "Cleanup complete"
}

# Set trap to cleanup on exit
trap cleanup EXIT

# Step 1: Start Docker services
print_info "Starting Docker services (RabbitMQ + LocalStack)..."
docker-compose up -d

# Step 2: Wait for services
wait_for_service "$RABBITMQ_URL" "RabbitMQ" || exit 1
wait_for_service "$AWS_ENDPOINT/_localstack/health" "LocalStack" || exit 1

# Step 3: Configure AWS CLI for LocalStack
print_info "Configuring AWS CLI for LocalStack..."
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

# Step 4: Create S3 bucket
print_info "Creating S3 bucket: $BUCKET_NAME..."
aws --endpoint-url=$AWS_ENDPOINT s3 mb s3://$BUCKET_NAME 2>/dev/null || print_info "Bucket already exists"

# Verify bucket
if aws --endpoint-url=$AWS_ENDPOINT s3 ls | grep -q $BUCKET_NAME; then
    print_success "S3 bucket created/verified"
else
    print_error "Failed to create S3 bucket"
    exit 1
fi

# Step 5: Create test file
print_info "Creating test file with 1000 lines..."
for i in {1..1000}; do 
    echo "Line $i: This is test data for processing - $(date +%s%N)" >> $TEST_FILE
done
print_success "Test file created ($(wc -l < $TEST_FILE) lines)"

# Step 6: Upload test file to S3
print_info "Uploading test file to S3..."
if aws --endpoint-url=$AWS_ENDPOINT s3 cp $TEST_FILE s3://$BUCKET_NAME/$TEST_FILE; then
    print_success "File uploaded to S3"
else
    print_error "Failed to upload file to S3"
    exit 1
fi

# Verify upload
FILE_SIZE=$(aws --endpoint-url=$AWS_ENDPOINT s3 ls s3://$BUCKET_NAME/$TEST_FILE | awk '{print $3}')
print_info "File size in S3: $FILE_SIZE bytes"

# Step 7: Build application
print_info "Building Spring Boot application..."
if mvn clean package -DskipTests -q; then
    print_success "Application built successfully"
else
    print_error "Failed to build application"
    exit 1
fi

# Step 8: Create local configuration
print_info "Creating local configuration..."
cat > src/main/resources/application-local.yml << EOF
aws:
  s3:
    region: us-east-1
    bucket: $BUCKET_NAME
    chunk-size: 1048576
  credentials:
    access-key: test
    secret-key: test
    use-instance-profile: false

spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

file:
  processing:
    batch-size: 10
    exchange: file-processing-exchange
    routing-key: processed-file
    queue: processed-files-queue

logging:
  level:
    root: INFO
    com.fileprocessing: DEBUG
EOF

# Step 9: Start application
print_info "Starting Spring Boot application..."
java -jar target/s3-file-processor-1.0.0.jar --spring.profiles.active=local > app.log 2>&1 &
APP_PID=$!
print_info "Application started with PID: $APP_PID"

# Step 10: Wait for application to be ready
wait_for_service "http://localhost:8080/actuator/health" "Spring Boot Application" || exit 1

# Step 11: Run the test
print_info "Triggering file processing..."
RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/files/process \
  -H "Content-Type: application/json" \
  -d "{\"s3Key\": \"$TEST_FILE\"}")

echo "API Response: $RESPONSE"

# Wait for processing to complete
print_info "Waiting for processing to complete (10 seconds)..."
sleep 10

# Step 12: Verify results in RabbitMQ
print_info "Checking RabbitMQ queue..."
QUEUE_INFO=$(curl -s -u guest:guest $RABBITMQ_URL/api/queues/%2F/processed-files-queue)

if [ -z "$QUEUE_INFO" ]; then
    print_error "Failed to get queue information"
    print_info "Check application logs:"
    tail -20 app.log
    exit 1
fi

MESSAGE_COUNT=$(echo $QUEUE_INFO | grep -o '"messages":[0-9]*' | grep -o '[0-9]*' | head -1)
READY_COUNT=$(echo $QUEUE_INFO | grep -o '"messages_ready":[0-9]*' | grep -o '[0-9]*' | head -1)

print_info "Messages in queue: $MESSAGE_COUNT"
print_info "Messages ready: $READY_COUNT"

# Step 13: Verify success
echo ""
echo "======================================"
echo "Test Results:"
echo "======================================"
echo "Test file lines: $(wc -l < $TEST_FILE)"
echo "Messages in RabbitMQ: $MESSAGE_COUNT"
echo "Messages ready: $READY_COUNT"
echo ""

if [ "$MESSAGE_COUNT" -gt 0 ]; then
    print_success "TEST PASSED! Successfully processed $MESSAGE_COUNT messages"
    
    # Show sample messages
    print_info "Fetching sample messages from queue..."
    SAMPLE=$(curl -s -u guest:guest -X POST $RABBITMQ_URL/api/queues/%2F/processed-files-queue/get \
        -H "Content-Type: application/json" \
        -d '{"count":3,"ackmode":"ack_requeue_true","encoding":"auto"}')
    
    if [ ! -z "$SAMPLE" ] && [ "$SAMPLE" != "[]" ]; then
        echo ""
        print_info "Sample messages:"
        echo "$SAMPLE" | python3 -m json.tool 2>/dev/null || echo "$SAMPLE"
    fi
    
    echo ""
    print_success "All tests completed successfully! 🎉"
    exit 0
else
    print_error "TEST FAILED! No messages found in queue"
    print_info "Application logs (last 30 lines):"
    tail -30 app.log
    exit 1
fi

# Made with Bob
