import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import { Construct } from 'constructs';

interface NetworkStackProps extends cdk.StackProps {
  stage: string;
}

export class NetworkStack extends cdk.Stack {

  readonly vpc:               ec2.Vpc;
  readonly albSecurityGroup:  ec2.SecurityGroup;
  readonly appSecurityGroup:  ec2.SecurityGroup;
  readonly dbSecurityGroup:   ec2.SecurityGroup;
  readonly cacheSecurityGroup: ec2.SecurityGroup;

  constructor(scope: Construct, id: string, props: NetworkStackProps) {
    super(scope, id, props);

    const { stage } = props;

    // ---- VPC ----
    this.vpc = new ec2.Vpc(this, 'Vpc', {
      vpcName:    `shopping-vpc-${stage}`,
      maxAzs:     2,
      natGateways: stage === 'prod' ? 2 : 1,
      subnetConfiguration: [
        { name: 'public',       subnetType: ec2.SubnetType.PUBLIC,              cidrMask: 24 },
        { name: 'private-app',  subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS, cidrMask: 24 },
        { name: 'private-data', subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS, cidrMask: 24 },
      ],
    });

    // VPC endpoint for SQS — keeps SQS traffic inside the VPC
    this.vpc.addInterfaceEndpoint('SqsEndpoint', {
      service: ec2.InterfaceVpcEndpointAwsService.SQS,
    });

    // ---- Security Groups ----

    // ALB: accepts internet traffic on 80/443
    this.albSecurityGroup = new ec2.SecurityGroup(this, 'AlbSg', {
      vpc: this.vpc,
      securityGroupName: `shopping-alb-sg-${stage}`,
      description: 'Allow internet traffic to ALB',
    });
    this.albSecurityGroup.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(80),  'HTTP');
    this.albSecurityGroup.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(443), 'HTTPS');

    // App: accepts traffic only from ALB
    this.appSecurityGroup = new ec2.SecurityGroup(this, 'AppSg', {
      vpc: this.vpc,
      securityGroupName: `shopping-app-sg-${stage}`,
      description: 'Allow traffic from ALB to ECS tasks',
    });
    this.appSecurityGroup.addIngressRule(this.albSecurityGroup, ec2.Port.tcp(8080), 'From ALB');

    // DB: accepts only from app
    this.dbSecurityGroup = new ec2.SecurityGroup(this, 'DbSg', {
      vpc: this.vpc,
      securityGroupName: `shopping-db-sg-${stage}`,
      description: 'Allow MySQL traffic from ECS tasks',
    });
    this.dbSecurityGroup.addIngressRule(this.appSecurityGroup, ec2.Port.tcp(3306), 'MySQL from app');

    // Cache: accepts only from app
    this.cacheSecurityGroup = new ec2.SecurityGroup(this, 'CacheSg', {
      vpc: this.vpc,
      securityGroupName: `shopping-cache-sg-${stage}`,
      description: 'Allow Redis traffic from ECS tasks',
    });
    this.cacheSecurityGroup.addIngressRule(this.appSecurityGroup, ec2.Port.tcp(6379), 'Redis from app');

    // ---- Outputs ----
    new cdk.CfnOutput(this, 'VpcId', { value: this.vpc.vpcId });
  }
}
