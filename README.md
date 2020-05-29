# AWS CDK Maven Plugin

The AWS CDK Maven plugin produces and deploys CloudFormation templates based on your cloud infrastructure defined by 
means of [CDK](https://aws.amazon.com/cdk/). The goal of the project is to improve the experience of Java developers 
while working with CDK by eliminating the need for installing [Node.js](https://nodejs.org/en/download) and interacting 
with the CDK application by means of [CDK Toolkit](https://docs.aws.amazon.com/cdk/latest/guide/tools.html).

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

The plugin provides two goals:

* `synth`: Synthesizes [CloudFormation](https://aws.amazon.com/cloudformation/) templates from the stacks defined in your
CDK application.
* `deploy`: Deploys the cloud resources defined in the synthesized templates to AWS.

Both goals require the parameter `<app>` to be specified, which is a full class name of the CDK application class 
defining the cloud infrastructure. The application class must either extend `software.amazon.awscdk.core.App` or define 
a `main` method which is supposed to create an instance of `App`, define cloud 
[constructs](https://docs.aws.amazon.com/cdk/latest/guide/constructs.html) and call `App#synth()` method in order to 
produce a cloud assembly with CloudFormation templates.

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
