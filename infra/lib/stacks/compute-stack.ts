import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as applicationautoscaling from 'aws-cdk-lib/aws-applicationautoscaling';
import { Construct } from 'constructs';

interface ComputeStackProps extends cdk.StackProps {
  stage: string;
  vpc: ec2.Vpc;
  appSecurityGroup: ec2.SecurityGroup;
  albSecurityGroup: ec2.SecurityGroup;
  dbSecret: secretsmanager.ISecret;
  dbEndpoint: string;
  redisEndpoint: string;
  orderQueue: sqs.Queue;
}

export class ComputeStack extends cdk.Stack {

  readonly ecsCluster:   ecs.Cluster;
  readonly ecsService:   ecs.FargateService;
  readonly ecrRepository: ecr.Repository;

  constructor(scope: Construct, id: string, props: ComputeStackProps) {
    super(scope, id, props);

    const { stage, vpc, appSecurityGroup, albSecurityGroup,
            dbSecret, dbEndpoint, redisEndpoint, orderQueue } = props;
    const isProd = stage === 'prod';

    // ---- ECR Repository ----
    this.ecrRepository = new ecr.Repository(this, 'EcrRepo', {
      repositoryName: `shopping-api-${stage}`,
      lifecycleRules: [{
        maxImageCount: 10,
        description:   'Keep last 10 images',
      }],
      removalPolicy: isProd ? cdk.RemovalPolicy.RETAIN : cdk.RemovalPolicy.DESTROY,
    });

    // ---- ECS Cluster ----
    this.ecsCluster = new ecs.Cluster(this, 'EcsCluster', {
      clusterName:       `shopping-cluster-${stage}`,
      vpc,
      containerInsights: true,
    });

    // ---- CloudWatch Log Group ----
    const logGroup = new logs.LogGroup(this, 'AppLogGroup', {
      logGroupName:  `/ecs/shopping-api-${stage}`,
      retention:     isProd ? logs.RetentionDays.ONE_MONTH : logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    // ---- Task Role (what the running container can DO) ----
    const taskRole = new iam.Role(this, 'TaskRole', {
      roleName:  `shopping-task-role-${stage}`,
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
    });
    orderQueue.grantSendMessages(taskRole);
    orderQueue.grantConsumeMessages(taskRole);
    taskRole.addToPolicy(new iam.PolicyStatement({
      actions: ['cloudwatch:PutMetricData'],
      resources: ['*'],
    }));

    // ---- Task Execution Role (for ECR pull + Secrets Manager) ----
    const executionRole = new iam.Role(this, 'TaskExecutionRole', {
      roleName:  `shopping-task-execution-role-${stage}`,
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('service-role/AmazonECSTaskExecutionRolePolicy'),
      ],
    });
    dbSecret.grantRead(executionRole);

    // ---- Fargate Task Definition ----
    const taskDef = new ecs.FargateTaskDefinition(this, 'TaskDef', {
      family:          `shopping-api-${stage}`,
      cpu:             1024,   // 1 vCPU
      memoryLimitMiB:  2048,   // 2 GB
      taskRole,
      executionRole,
    });

    taskDef.addContainer('AppContainer', {
      containerName: 'shopping-api',
      image:  ecs.ContainerImage.fromEcrRepository(this.ecrRepository, 'latest'),
      portMappings: [{ containerPort: 8080 }],
      environment: {
        SPRING_PROFILES_ACTIVE:      'prod',
        AWS_REGION:                  this.region,
        SQS_QUEUE_URL:               orderQueue.queueUrl,
        REDIS_HOST:                  redisEndpoint,
        REDIS_PORT:                  '6379',
        CLOUDWATCH_METRICS_ENABLED:  'true',
        DB_HOST:                     dbEndpoint,
        DB_PORT:                     '3306',
        DB_NAME:                     'shopping',
      },
      secrets: {
        DB_USER:     ecs.Secret.fromSecretsManager(dbSecret, 'username'),
        DB_PASSWORD: ecs.Secret.fromSecretsManager(dbSecret, 'password'),
      },
      logging: ecs.LogDrivers.awsLogs({
        logGroup,
        streamPrefix: 'shopping-api',
      }),
      healthCheck: {
        command:     ['CMD-SHELL', 'wget -qO- http://localhost:8080/actuator/health || exit 1'],
        interval:    cdk.Duration.seconds(30),
        timeout:     cdk.Duration.seconds(10),
        startPeriod: cdk.Duration.seconds(60),
        retries:     3,
      },
    });

    // ---- ALB ----
    const alb = new elbv2.ApplicationLoadBalancer(this, 'Alb', {
      loadBalancerName: `shopping-alb-${stage}`,
      vpc,
      internetFacing:   true,
      securityGroup:    albSecurityGroup,
      vpcSubnets:       { subnetType: ec2.SubnetType.PUBLIC },
    });

    const targetGroup = new elbv2.ApplicationTargetGroup(this, 'TargetGroup', {
      vpc,
      port:              8080,
      protocol:          elbv2.ApplicationProtocol.HTTP,
      targetType:        elbv2.TargetType.IP,
      healthCheck: {
        path:                '/actuator/health',
        interval:            cdk.Duration.seconds(30),
        healthyHttpCodes:    '200',
        healthyThresholdCount: 2,
      },
      deregistrationDelay: cdk.Duration.seconds(30),
    });

    alb.addListener('HttpListener', {
      port:            80,
      defaultAction:   elbv2.ListenerAction.forward([targetGroup]),
    });

    // ---- Fargate Service ----
    this.ecsService = new ecs.FargateService(this, 'EcsService', {
      serviceName:   `shopping-api-${stage}`,
      cluster:        this.ecsCluster,
      taskDefinition: taskDef,
      desiredCount:   isProd ? 2 : 1,
      securityGroups: [appSecurityGroup],
      vpcSubnets:     { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      circuitBreaker: { rollback: true },
      healthCheckGracePeriod: cdk.Duration.seconds(60),
    });

    this.ecsService.attachToApplicationTargetGroup(targetGroup);

    // ---- Auto Scaling ----
    const scaling = this.ecsService.autoScaleTaskCount({
      minCapacity: isProd ? 2 : 1,
      maxCapacity: isProd ? 10 : 3,
    });

    scaling.scaleOnCpuUtilization('CpuScaling', {
      targetUtilizationPercent: 70,
      scaleInCooldown:  cdk.Duration.seconds(60),
      scaleOutCooldown: cdk.Duration.seconds(30),
    });

    scaling.scaleOnMetric('SqsScaling', {
      metric: orderQueue.metricApproximateNumberOfMessagesNotVisible(),
      scalingSteps: [
        { upper: 0,   change: 0  },
        { lower: 100, change: +1 },
        { lower: 500, change: +2 },
      ],
      adjustmentType: applicationautoscaling.AdjustmentType.CHANGE_IN_CAPACITY,
    });

    // ---- Outputs ----
    new cdk.CfnOutput(this, 'AlbDnsName',  { value: alb.loadBalancerDnsName });
    new cdk.CfnOutput(this, 'EcrRepoUri',  { value: this.ecrRepository.repositoryUri });
    new cdk.CfnOutput(this, 'ClusterName', { value: this.ecsCluster.clusterName });
    new cdk.CfnOutput(this, 'ServiceName', { value: this.ecsService.serviceName });
  }
}
