# Javagen

``` yaml
use: $(this-folder)/../preprocessor

pipeline:
  javagen:
    scope: azure-functions-java
    input: preprocessor
    output-artifact: java-files
  
  javagen/emitter:
    input: javagen
    scope: scope-javagen/emitter

scope-javagen/emitter:
    input-artifact: java-files
    output-uri-expr: $key

output-artifact: java-files
```
