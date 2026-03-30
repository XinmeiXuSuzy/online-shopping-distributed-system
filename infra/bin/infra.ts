#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { NetworkStack   } from '../lib/stacks/network-stack';
import { DataStack      } from '../lib/stacks/data-stack';
import { MessagingStack } from '../lib/stacks/messaging-stack';
import { ComputeStack   } from '../lib/stacks/compute-stack';
import { PipelineStack  } from '../lib/stacks/pipeline-stack';

const app = new cdk.App();

const env: cdk.Environment = {
  account: process.env.CDK_DEFAULT_ACCOUNT,
  region:  process.env.CDK_DEFAULT_REGION ?? 'us-east-1',
};

const stage = app.node.tryGetContext('stage') ?? 'dev';

const network = new NetworkStack(app, `Shopping-Network-${stage}`, { env, stage });

const data = new DataStack(app, `Shopping-Data-${stage}`, {
  env,
  stage,
  vpc: network.vpc,
  dbSecurityGroup:    network.dbSecurityGroup,
  cacheSecurityGroup: network.cacheSecurityGroup,
});

const messaging = new MessagingStack(app, `Shopping-Messaging-${stage}`, { env, stage });

const compute = new ComputeStack(app, `Shopping-Compute-${stage}`, {
  env,
  stage,
  vpc:            network.vpc,
  appSecurityGroup: network.appSecurityGroup,
  albSecurityGroup: network.albSecurityGroup,
  dbSecret:       data.dbSecret,
  dbEndpoint:     data.dbEndpoint,
  redisEndpoint:  data.redisEndpoint,
  orderQueue:     messaging.orderQueue,
});

new PipelineStack(app, `Shopping-Pipeline-${stage}`, {
  env,
  stage,
  ecsService: compute.ecsService,
  ecsCluster: compute.ecsCluster,
  repository: compute.ecrRepository,
});

app.synth();
