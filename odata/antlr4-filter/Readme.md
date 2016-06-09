# REQUIREMENTS

antlr-4.5.3-complete.jar
gson-2.6.2.jar

# INSTALLATION

```bash
@del /Q /S /f ..\src\cl\kaiser\odata\gen\*
$ java  org.antlr.v4.Tool -package cl.kaiser.odata.gen -o ../src/cl/kaiser/odata/gen -no-listener -visitor ../src/cl/kaiser/odata/OdataFilter.g4
```

# EXAMPLE

```java
```

