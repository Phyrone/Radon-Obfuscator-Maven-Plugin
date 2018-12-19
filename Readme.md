# Radon Obfuscator Maven Plugin
## Features
- Auto Download Radon (https://github.com/ItzSomebody/Radon)
- Extract rt Jar if not exist (https://github.com/Storyyeller/jrt-extractor)
- add All Maven Dependecys
- Import Prepared Radonconfig
## How to Use
```xml 
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```
```xml
<plugin>
    <groupId>com.github.phyrone</groupId>
    <artifactId>Radon-Obfuscator-Maven-Plugin</artifactId>
    <version>${plugin.version}</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>obfuscate</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```
## TODO
- add to Maven Central
- Radon Configuration in Pom