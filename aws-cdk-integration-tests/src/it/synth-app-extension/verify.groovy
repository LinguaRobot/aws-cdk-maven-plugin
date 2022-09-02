def cloudAssemblyDirectory = new File(basedir, "target/cdk.out")
assert cloudAssemblyDirectory.exists() && cloudAssemblyDirectory.directory

def templateFile = new File(cloudAssemblyDirectory, "synth-app-extension-test-stack.template.json")
assert templateFile.exists() && templateFile.file
