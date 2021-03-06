# Azure Functions' AutoRest plugin for Java.
This is the AutoRest Java generator for Azure Functions. It's built on AutoRest v3, written in Java, and supports OpenAPI3. It generates Azure Functions apps.

## Prerequisites
You need to have the following installed on your machine:

- Node.JS v10.x - v13.x
- Java 8+
- Maven 3.x

_**Optional Prerequisites**_
- Azure Functions Core Tools ([Details here](https://github.com/Azure/azure-functions-core-tools)) for running the created Function app.

You need to have [autorest-beta](https://www.npmjs.com/package/@autorest/autorest) installed through NPM:

```bash
npm i -g @autorest/autorest
```

## Usage
Clone this repo and checkout to v4 branch. Make sure all prerequisites are met, and run

```bash
mvn package -Dlocal
```

This will build a file `javagen-jar-with-dependencies.jar` under `javagen` module, a `preprocess-jar-with-dependencies.jar` under `preprocessor` module. And then run AutoRest

```bash
autorest --azure-functions-java
    --use:where/this/repo/is/cloned/autorest.azure-functions-java
    --input-file:path/to/specs.json
    --output-folder:where/to/generate/java/files
    --namespace:specified.java.package
```

Java files will be generated under `where/to/generate/java/files/src/main/java/specified/java/package`. 

To debug, add `--java.debugger` to the argument list. The JVM will suspend at the beginning of the execution. Then attach a remote debugger in your IDE to `localhost:5005`. **Make sure you detach the debugger before killing the AutoRest process. Otherwise it will fail to shutdown the JVM and leave it orphaned. (which can be killed in the Task Manager)**

## Project structure
### extension-base
This contains the base classes and utilities for creating an AutoRest extension in Java. It handles the JSON RPC communications with AutoRest core, provides JSON and YAML parsers, and provides the POJO models for the code model output from [modelerfour](https://github.com/Azure/autorest.modelerfour/).

Extend from `NewPlugin.java` class if you are writing a new extension in Java.

### javagen
This contains the actually generator extension, including mappers that maps a code model to a Java server models, and templates that writes the Azure Fucntions Java client models into .java files.

### tests
This contains the generated classes from the [test swaggers](https://github.com/Azure/autorest.testserver/tree/master/swagger) in `src/main`. The code here should always be kept up-to-date with the output of the generator in `javagen`. 

This also contains test code for these generated code under `src/test`. Running the tests will hit the test server running locally (see https://github.com/Azure/autorest.testserver for instructions) and verify the correctness of the generated code.

## Contributing

This project welcomes contributions and suggestions.  Most contributions require you to agree to a
Contributor License Agreement (CLA) declaring that you have the right to, and actually do, grant us
the rights to use your contribution. For details, visit https://cla.microsoft.com.

When you submit a pull request, a CLA-bot will automatically determine whether you need to provide
a CLA and decorate the PR appropriately (e.g., label, comment). Simply follow the instructions
provided by the bot. You will only need to do this once across all repos using our CLA.

This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/).
For more information see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or
contact [opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.

## Autorest plugin configuration
- Please don't edit this section unless you're re-configuring how the powershell extension plugs in to AutoRest
AutoRest needs the below config to pick this up as a plug-in - see [AutoRest-extension.md](https://github.com/Azure/autorest/blob/master/docs/developer/architecture/AutoRest-extension.md) for more information on extension model.

### Javagen

``` yaml
use: $(this-folder)/javagen
```