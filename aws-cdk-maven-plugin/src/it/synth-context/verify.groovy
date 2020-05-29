import groovy.json.JsonSlurper
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain
import software.amazon.awssdk.services.sts.StsClient

final JSON = new JsonSlurper()

def contextFile = new File(basedir, "target/cdk.out/cdk.context.json");
assert contextFile.exists() && contextFile.file

def expectedAccountId = StsClient.create().getCallerIdentity().account();
def region = new DefaultAwsRegionProviderChain().region;

def context = JSON.parse(contextFile);
assert context.keySet() == [
        "@aws-cdk/core:enableStackNameDuplicates",
        "aws-cdk:enableDiffNoFail",
        "availability-zones:account=${expectedAccountId}:region=${region}"
] as Set
