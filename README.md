# AWS CDK Maven Plugin

The AWS CDK Maven plugin produces and deploys CloudFormation templates based on your cloud infrastructure defined by 
means of [CDK][1]. The goal of the project is to improve the experience of Java developers while working with CDK by 
eliminating the need for installing [Node.js][2] and interacting with the CDK application by means of [CDK Toolkit][3].

## Prerequisites

The plugin requires Java >= 8 and Maven >= 3.5.

## Authentication

The plugin tries to find the credentials and region in different sources in the following order:

* If `<profile>` configuration parameter is defined, the plugin looks for the corresponding credentials and region in 
the default AWS credentials and config files (`~/.aws/credentials` and `~/.aws/config`, the location may be different 
depending on the platform). 
* Using Java system properties `aws.accessKeyId`, `aws.secretKey` and `aws.region`.
* Using environment variables `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` and `AWS_DEFAULT_REGION`
* Looking for the credentials and region associated with the default profile in the credentials and config files.

## Usage

The plugin provides three goals:

* `synth`: Synthesizes [CloudFormation][4] templates based on the resources defined in your CDK application.
* `bootstrap`: Deploys toolkit stacks required by the CDK application to an AWS.
* `deploy`: Deploys the CDK application to an AWS (based on the synthesized resources)

### Synthesis

During the execution of `synth` goal, a cloud assembly is synthesized into a directory (`target/cdk.out` by default). 
The cloud assembly includes CloudFormation templates, AWS Lambda bundles, file and Docker image assets, and other 
deployment artifacts.

The only mandatory parameter required by the goal is `<app>`, which is a full class name of the CDK app class defining 
the cloud infrastructure. The application class must either extend `software.amazon.awscdk.core.App` or define a 
`main` method which is supposed to create an instance of `App`, define cloud [constructs][5] and call `App#synth()` 
method in order to produce a cloud assembly with CloudFormation templates.

Extending `App` class:
```java
import software.amazon.awscdk.core.App;

public class MyApp extends App {

    public Mypp() {
        new MyStack(this, "my-stack");
    }

}
```

Defining `main` method:

```java
import software.amazon.awscdk.core.App;

public class MyApp {

    public static void main(String[] args) {
        App app = new App();
        new MyStack(app, "my-stack");
        app.synth();
    }
    
}
```

### Bootstrapping

Some CDK applications may require a "toolkit stack" that includes the resources required for the application operation. 
For example, the toolkit stack may include S3 bucket used to store templates and assets for the deployment.

The plugin is able to detect if a stack requires a toolkit stack and if it does, the plugin will automatically deploy it 
(or update if needed) during the execution of `bootstrap` goal (provided that the required toolkit stack version wasn't 
already deployed). You may also choose to omit `bootstrap` goal if you don't want to rely on the plugin and control this 
process by yourself or just want to make sure that the toolkit stack is not created by a mistake. If you choose to omit 
`bootstrap` goal, you will need to install the toolkit stack the first time you deploy an AWS CDK application into an 
environment (account/region) by running `cdk bootstrap` command (please refer to [AWS CDK Toolkit][3] for the details).

### Deployment

To deploy the synthesized application into an AWS, add `deploy` goal to the execution (`deploy` and `bootstrap` goals are
attached to the `deploy` Maven phase).

[1]: https://aws.amazon.com/cdk/
[2]: https://nodejs.org/en/download
[3]: https://docs.aws.amazon.com/cdk/latest/guide/tools.html#cli
[4]: https://aws.amazon.com/cloudformation/
[5]: https://docs.aws.amazon.com/cdk/latest/guide/constructs.html