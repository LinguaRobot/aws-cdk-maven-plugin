# AWS CDK Maven Plugin
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.linguarobot/aws-cdk-maven-plugin/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/io.linguarobot/aws-cdk-maven-plugin)

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

## Getting Started

Add the plugin to your Maven project:

```xml
<plugin>
    <groupId>io.linguarobot</groupId>
    <artifactId>aws-cdk-maven-plugin</artifactId>
    <!-- Please use the latest available version: https://search.maven.org/artifact/io.linguarobot/aws-cdk-maven-plugin -->
    <version>${cdk.maven.plugin.version}</version>
    <executions>
        <execution>
            <id>deploy-cdk-app</id>
            <goals>
                <goal>synth</goal>
                <goal>bootstrap</goal>
                <goal>deploy</goal>
            </goals>
            <configuration>
                <!-- Full class name of the app class defining your stacks -->
                <app>${cdk.app}</app>
                <!-- Input parameters for the stacks. -->
                <parameters>
                    <ParameterName>...</ParameterName>
                    ...
                </parameters>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Please take a look at the [example project][6]. It is based on the project generated using `cdk init` with the 
difference that it uses `aws-cdk-maven-plugin` instead of the CDK CLI. You can also find more examples in the
[integration test](./aws-cdk-maven-plugin/src/it) directory.

## Usage

The plugin provides three goals:

* `synth`: Synthesizes [CloudFormation][4] templates based on the resources defined in your CDK application.
* `bootstrap`: Deploys toolkit stacks required by the CDK application to an AWS.
* `deploy`: Deploys the CDK application to an AWS (based on the synthesized resources)

### Synthesis

During the execution of `synth` goal, a cloud assembly is synthesized. The cloud assembly is a directory 
(`target/cdk.out` by default) containing the artifacts required for the deployment, i.e. CloudFormation templates, AWS 
Lambda bundles, file and Docker image assets etc. The artifacts in the cloud assembly directory are later used by 
`bootstrap` and `deploy` goals.

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

#### Configuration

| Parameter | Type | Since | Description |
| --- | --- | --- | --- |
| `<app>` <br/> `-Daws.cdk.app` | `String` | `0.0.1` | Full class name of the CDK app class defining the cloud infrastructure. |
| `<profile>` <br/> `-Daws.cdk.profile` | `String` | `0.0.1` | A profile that will be used to find credentials and region. |
| `<cloudAssemblyDirectory>` <br/> `-Daws.cdk.cloud.assembly.directory` | `String` | `0.0.1` | A directory where the cloud assembly will be synthesized. |
| `<arguments>` <br/> `-Daws.cdk.arguments` | `List<String>` | `0.0.5` | A list of arguments to be passed to the CDK application. |
| `<skip>` <br/> `-Daws.cdk.skip` | `boolean` | `0.0.7` | Enables/disables the execution of the goal. | 

### Bootstrapping

Some CDK applications may require a "toolkit stack" that includes the resources required for the application operation. 
For example, the toolkit stack may include S3 bucket used to store templates and assets for the deployment.

The plugin is able to detect if a stack requires a toolkit stack and if it does, the plugin will automatically deploy it 
(or update if needed) during the execution of `bootstrap` goal (provided that the required toolkit stack version wasn't 
already deployed). You may also choose to omit `bootstrap` goal if you don't want to rely on the plugin and control this 
process by yourself or just want to make sure that the toolkit stack is not created by a mistake. If you choose to omit 
`bootstrap` goal, you will need to install the toolkit stack the first time you deploy an AWS CDK application into an 
environment (account/region) by running `cdk bootstrap` command (please refer to [AWS CDK Toolkit][3] for the details).

#### Configuration

| Parameter | Type | Since | Description |
| --- | --- | --- | --- |
| `<profile>` <br/> `-Daws.cdk.profile` | `String` | `0.0.1` | A profile that will be used to find credentials and region. |
| `<cloudAssemblyDirectory>` <br/> `-Daws.cdk.cloud.assembly.directory` | `String` | `0.0.1` | A cloud assembly directory with the deployment artifacts (`target/cdk.out` by default). |
| `<toolkitStackName>` <br/> `-Daws.cdk.toolkit.stack.name` | `String` | `0.0.1` | The name of the CDK toolkit stack (`CDKToolkit` by default). |
| `<stacks>` <br/> `-Daws.cdk.stacks` | `List<String>` | `0.0.4` | Stacks to deploy. The plugin will create the toolkit stacks only for those stacks that are being deployed (by default, all the stacks defined in your application will be deployed). |
| `<skip>` <br/> `-Daws.cdk.skip` | `boolean` | `0.0.7` | Enables/disables the execution of the goal. |

### Deployment

To deploy the synthesized application into an AWS, add `deploy` goal to the execution (`deploy` and `bootstrap` goals are
attached to the `deploy` Maven phase).

#### Configuration

| Parameter | Type | Since | Description |
| --- | --- | --- | --- |
| `<profile>` <br/> `-Daws.cdk.profile` | `String` | `0.0.1` | A profile that will be used to find credentials and region. |
| `<cloudAssemblyDirectory>` <br/> `-Daws.cdk.cloud.assembly.directory` | `String` | `0.0.1` | A cloud assembly directory with the deployment artifacts (`target/cdk.out` by default). |
| `<toolkitStackName>` <br/> `-Daws.cdk.toolkit.stack.name` | `String` | `0.0.1` | The name of the CDK toolkit stack to use (`CDKToolkit` is used by default). |
| `<stacks>` <br/> `-Daws.cdk.stacks` | `List<String>` | `0.0.4` | Stacks to deploy. By default, all the stacks defined in your application will be deployed. |
| `<parameters>` | `Map<String, String>` | `0.0.4` | Input parameters for the stacks. For the new stacks, all the parameters without a default value must be specified. In the case of an update, existing values will be reused. |
| `<skip>` <br/> `-Daws.cdk.skip` | `boolean` | `0.0.7` | Enables/disables the execution of the goal. |


[1]: https://aws.amazon.com/cdk/
[2]: https://nodejs.org/en/download
[3]: https://docs.aws.amazon.com/cdk/latest/guide/tools.html#cli
[4]: https://aws.amazon.com/cloudformation/
[5]: https://docs.aws.amazon.com/cdk/latest/guide/constructs.html
[6]: https://github.com/LinguaRobot/aws-cdk-maven-plugin-example