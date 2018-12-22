# Radon Obfuscator Maven Plugin
## Features
- Auto Download Radon (https://github.com/ItzSomebody/Radon)
- Extract rt Jar if not exist (https://github.com/Storyyeller/jrt-extractor)
- add All Maven Dependecys
- Import Prepared Radonconfig
## How to Use
```xml 
<pluginRepositories>
    <pluginRepository>
        <id>phyrone.plugins</id>
        <url>http://maven.phyrone.de/artifactory/plugin/</url>
    </pluginRepository>
</pluginRepositories>
```
```xml
<plugin>
    <groupId>de.phyrone</groupId>
    <artifactId>radon-obfuscator-maven-plugin</artifactId>
    <version>1.2-SNAPSHOT</version>
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
