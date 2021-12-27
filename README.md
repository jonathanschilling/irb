# irb

A reader in pure Java for the `*.irb` file format by InfraTec,  
inspired by https://github.com/tomsoftware/Irbis-File-Format .

This is a pure hobby project. No copyright infringements or similar is intended.
Please inform the author about possible legal issues before turning to a lawyer.

## Usage

1. Make a jar:

```bash
> mvn clean package
```

2. Execute the jar with the `*.irb` file as first command line argument:
 
```bash
> java -jar target/irb-1.0.0.jar AB020300.irb
```

Otherwise, you can include this project as a dependency in Maven:

```xml
<dependency>
    <artifactId>irb</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>
</dependency>
```

## Contributers

 * [jonathanschilling](https://github.com/jonathanschilling)
 * [benjaminSchilling33](https://github.com/benjaminschilling33)
 
