import * as cdk from 'aws-cdk-lib';
import * as codepipeline from 'aws-cdk-lib/aws-codepipeline';
import * as codepipeline_actions from 'aws-cdk-lib/aws-codepipeline-actions';
import * as codebuild from 'aws-cdk-lib/aws-codebuild';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as s3 from 'aws-cdk-lib/aws-s3';
import { Construct } from 'constructs';

interface PipelineStackProps extends cdk.StackProps {
  stage: string;
  ecsService:  ecs.FargateService;
  ecsCluster:  ecs.Cluster;
  repository:  ecr.Repository;
}

export class PipelineStack extends cdk.Stack {

  constructor(scope: Construct, id: string, props: PipelineStackProps) {
    super(scope, id, props);

    const { stage, ecsService, ecsCluster, repository } = props;

    // ---- S3 Artifact Bucket ----
    const artifactBucket = new s3.Bucket(this, 'ArtifactBucket', {
      bucketName:        `shopping-pipeline-artifacts-${stage}-${this.account}`,
      encryption:        s3.BucketEncryption.S3_MANAGED,
      lifecycleRules:    [{ expiration: cdk.Duration.days(30) }],
      removalPolicy:     cdk.RemovalPolicy.DESTROY,
      autoDeleteObjects: true,
    });

    // ---- CodeBuild Project ----
    const buildProject = new codebuild.PipelineProject(this, 'BuildProject', {
      projectName:  `shopping-api-build-${stage}`,
      environment: {
        buildImage:     codebuild.LinuxBuildImage.STANDARD_7_0,
        privileged:     true,   // required for Docker build
        computeType:    codebuild.ComputeType.MEDIUM,
        environmentVariables: {
          ECR_REPO_URI: { value: repository.repositoryUri },
          AWS_REGION:   { value: this.region },
          IMAGE_TAG:    { value: 'latest' },
        },
      },
      buildSpec: codebuild.BuildSpec.fromObject({
        version: '0.2',
        phases: {
          install: {
            'runtime-versions': { java: 'corretto17' },
          },
          pre_build: {
            commands: [
              'echo Logging in to Amazon ECR...',
              'aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ECR_REPO_URI',
              'COMMIT_HASH=$(echo $CODEBUILD_RESOLVED_SOURCE_VERSION | cut -c 1-7)',
              'IMAGE_TAG=${COMMIT_HASH:-latest}',
            ],
          },
          build: {
            commands: [
              'echo Running tests...',
              'mvn -B test --no-transfer-progress',
              'echo Building JAR...',
              'mvn -B package -DskipTests --no-transfer-progress',
              'echo Building Docker image...',
              'docker build -t $ECR_REPO_URI:$IMAGE_TAG -f docker/Dockerfile .',
              'docker tag $ECR_REPO_URI:$IMAGE_TAG $ECR_REPO_URI:latest',
            ],
          },
          post_build: {
            commands: [
              'echo Pushing Docker image...',
              'docker push $ECR_REPO_URI:$IMAGE_TAG',
              'docker push $ECR_REPO_URI:latest',
              // imageDetail.json is consumed by ECS deploy action
              'printf \'[{"name":"shopping-api","imageUri":"%s"}]\' "$ECR_REPO_URI:$IMAGE_TAG" > imagedefinitions.json',
            ],
          },
        },
        artifacts: {
          files: ['imagedefinitions.json'],
        },
        reports: {
          TestReport: {
            files: ['shopping-api/target/surefire-reports/**/*.xml'],
            'file-format': 'JUNITXML',
          },
        },
      }),
    });

    repository.grantPullPush(buildProject);
    buildProject.addToRolePolicy(new iam.PolicyStatement({
      actions:   ['ecr:GetAuthorizationToken'],
      resources: ['*'],
    }));

    // ---- Pipeline Artifacts ----
    const sourceOutput = new codepipeline.Artifact('SourceOutput');
    const buildOutput  = new codepipeline.Artifact('BuildOutput');

    // ---- CodePipeline ----
    // Source: GitHub connection (replace owner/repo/branch with your values)
    const pipeline = new codepipeline.Pipeline(this, 'Pipeline', {
      pipelineName:   `shopping-api-pipeline-${stage}`,
      artifactBucket,
      stages: [
        {
          stageName: 'Source',
          actions: [
            new codepipeline_actions.GitHubSourceAction({
              actionName:  'GitHub_Source',
              owner:       this.node.tryGetContext('githubOwner') ?? 'your-github-org',
              repo:        this.node.tryGetContext('githubRepo')  ?? 'online-shopping-distributed-system',
              branch:      stage === 'prod' ? 'main' : 'develop',
              oauthToken:  cdk.SecretValue.secretsManager(`shopping/${stage}/github-token`),
              output:      sourceOutput,
            }),
          ],
        },
        {
          stageName: 'Build',
          actions: [
            new codepipeline_actions.CodeBuildAction({
              actionName: 'Build_Test_Push',
              project:    buildProject,
              input:      sourceOutput,
              outputs:    [buildOutput],
            }),
          ],
        },
        {
          stageName: 'Deploy',
          actions: [
            new codepipeline_actions.EcsDeployAction({
              actionName:  'Deploy_to_ECS',
              service:     ecsService,
              input:       buildOutput,
              deploymentTimeout: cdk.Duration.minutes(10),
            }),
          ],
        },
      ],
    });

    // Allow pipeline to deploy to ECS
    ecsService.taskDefinition.taskRole.grant(
      pipeline.role,
      'iam:PassRole'
    );

    // ---- Outputs ----
    new cdk.CfnOutput(this, 'PipelineName', { value: pipeline.pipelineName });
  }
}
