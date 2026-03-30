#!/bin/bash
# Creates the SQS queue in LocalStack on startup

echo "Creating SQS queues..."

awslocal sqs create-queue \
  --queue-name order-processing-dlq \
  --region us-east-1

DLQ_ARN=$(awslocal sqs get-queue-attributes \
  --queue-url http://localhost:4566/000000000000/order-processing-dlq \
  --attribute-names QueueArn \
  --query 'Attributes.QueueArn' \
  --output text)

awslocal sqs create-queue \
  --queue-name order-processing-queue \
  --attributes "VisibilityTimeout=30,MessageRetentionPeriod=345600,RedrivePolicy={\"deadLetterTargetArn\":\"$DLQ_ARN\",\"maxReceiveCount\":\"3\"}" \
  --region us-east-1

echo "SQS queues created:"
awslocal sqs list-queues
