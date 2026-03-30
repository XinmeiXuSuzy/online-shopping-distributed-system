import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as elasticache from 'aws-cdk-lib/aws-elasticache';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';

interface DataStackProps extends cdk.StackProps {
  stage: string;
  vpc: ec2.Vpc;
  dbSecurityGroup: ec2.SecurityGroup;
  cacheSecurityGroup: ec2.SecurityGroup;
}

export class DataStack extends cdk.Stack {

  readonly dbSecret:      secretsmanager.ISecret;
  readonly dbEndpoint:    string;
  readonly redisEndpoint: string;

  constructor(scope: Construct, id: string, props: DataStackProps) {
    super(scope, id, props);

    const { stage, vpc, dbSecurityGroup, cacheSecurityGroup } = props;
    const isProd = stage === 'prod';

    // ---- RDS MySQL 8 ----
    const dbInstance = new rds.DatabaseInstance(this, 'Mysql', {
      engine: rds.DatabaseInstanceEngine.mysql({
        version: rds.MysqlEngineVersion.VER_8_0,
      }),
      instanceType: isProd
        ? ec2.InstanceType.of(ec2.InstanceClass.T3, ec2.InstanceSize.MEDIUM)
        : ec2.InstanceType.of(ec2.InstanceClass.T3, ec2.InstanceSize.MICRO),
      vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      securityGroups: [dbSecurityGroup],
      multiAz:           isProd,
      allocatedStorage:  20,
      maxAllocatedStorage: 100,
      databaseName:      'shopping',
      credentials:       rds.Credentials.fromGeneratedSecret('shopping_admin', {
        secretName: `shopping/${stage}/db-credentials`,
      }),
      backupRetention:   cdk.Duration.days(isProd ? 7 : 1),
      deletionProtection: isProd,
      removalPolicy:     isProd ? cdk.RemovalPolicy.RETAIN : cdk.RemovalPolicy.DESTROY,
      enablePerformanceInsights: isProd,
      storageEncrypted: true,
    });

    this.dbSecret   = dbInstance.secret!;
    this.dbEndpoint = dbInstance.dbInstanceEndpointAddress;

    // ---- ElastiCache Redis 7 ----
    const cacheSubnetGroup = new elasticache.CfnSubnetGroup(this, 'RedisSubnetGroup', {
      description: `shopping-redis-${stage}`,
      subnetIds:   vpc.selectSubnets({ subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS }).subnetIds,
    });

    const redisCluster = new elasticache.CfnReplicationGroup(this, 'Redis', {
      replicationGroupDescription: `shopping-redis-${stage}`,
      cacheNodeType:   isProd ? 'cache.r6g.large' : 'cache.t3.micro',
      engine:          'redis',
      engineVersion:   '7.1',
      numCacheClusters: isProd ? 2 : 1,   // 1 primary + 1 read replica in prod
      cacheSubnetGroupName: cacheSubnetGroup.ref,
      securityGroupIds: [cacheSecurityGroup.securityGroupId],
      atRestEncryptionEnabled: true,
      transitEncryptionEnabled: true,
      automaticFailoverEnabled: isProd,
    });

    this.redisEndpoint = redisCluster.attrPrimaryEndPointAddress;

    // ---- Outputs ----
    new cdk.CfnOutput(this, 'DbEndpoint',    { value: this.dbEndpoint });
    new cdk.CfnOutput(this, 'RedisEndpoint', { value: this.redisEndpoint });
    new cdk.CfnOutput(this, 'DbSecretArn',   { value: this.dbSecret.secretArn });
  }
}
