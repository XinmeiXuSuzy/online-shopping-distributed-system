import * as cdk from 'aws-cdk-lib';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as cloudwatch_actions from 'aws-cdk-lib/aws-cloudwatch-actions';
import * as sns from 'aws-cdk-lib/aws-sns';
import { Construct } from 'constructs';

interface MessagingStackProps extends cdk.StackProps {
  stage: string;
}

export class MessagingStack extends cdk.Stack {

  readonly orderQueue: sqs.Queue;

  constructor(scope: Construct, id: string, props: MessagingStackProps) {
    super(scope, id, props);

    const { stage } = props;

    // ---- Dead Letter Queue ----
    const dlq = new sqs.Queue(this, 'OrderDlq', {
      queueName:             `order-processing-dlq-${stage}`,
      retentionPeriod:       cdk.Duration.days(14),
      encryption:            sqs.QueueEncryption.SQS_MANAGED,
    });

    // CloudWatch alarm: any message in DLQ means a consumer failure
    const dlqAlarm = new cloudwatch.Alarm(this, 'DlqDepthAlarm', {
      alarmName:          `shopping-dlq-depth-${stage}`,
      alarmDescription:   'Messages in order DLQ — consumer failure requires investigation',
      metric:             dlq.metricApproximateNumberOfMessagesVisible(),
      threshold:          0,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      evaluationPeriods:  1,
      treatMissingData:   cloudwatch.TreatMissingData.NOT_BREACHING,
    });

    // ---- Main Order Queue ----
    this.orderQueue = new sqs.Queue(this, 'OrderQueue', {
      queueName:             `order-processing-queue-${stage}`,
      visibilityTimeout:     cdk.Duration.seconds(30),
      retentionPeriod:       cdk.Duration.days(4),
      encryption:            sqs.QueueEncryption.SQS_MANAGED,
      deadLetterQueue: {
        queue:           dlq,
        maxReceiveCount: 3,
      },
    });

    // Alarm: oldest message age > 5 minutes means consumer is falling behind
    new cloudwatch.Alarm(this, 'OldestMessageAlarm', {
      alarmName:          `shopping-queue-age-${stage}`,
      alarmDescription:   'SQS messages not being consumed fast enough',
      metric:             this.orderQueue.metricApproximateAgeOfOldestMessage(),
      threshold:          300,   // 5 minutes in seconds
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      evaluationPeriods:  2,
      treatMissingData:   cloudwatch.TreatMissingData.NOT_BREACHING,
    });

    // ---- Outputs ----
    new cdk.CfnOutput(this, 'OrderQueueUrl', { value: this.orderQueue.queueUrl });
    new cdk.CfnOutput(this, 'OrderDlqUrl',   { value: dlq.queueUrl });
  }
}
