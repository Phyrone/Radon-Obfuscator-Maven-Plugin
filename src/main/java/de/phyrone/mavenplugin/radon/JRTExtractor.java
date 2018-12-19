package de.phyrone.mavenplugin.radon;// Copyright 2017 Robert Grosse

// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at

//    http://www.apache.org/licenses/LICENSE-2.0

// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.zip.*;

public class JRTExtractor {
    static public void main(File outputFile, Log logger) throws Throwable {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));

        try (ZipOutputStream zipStream = new ZipOutputStream(
                Files.newOutputStream(outputFile.toPath()))) {

            Files.walk(fs.getPath("/")).forEach(p -> {
                if (!Files.isRegularFile(p)) {
                    return;
                }
                logger.debug("JarToRt: " + p.toUri().toASCIIString());
                try {
                    byte[] data = Files.readAllBytes(p);

                    List<String> list = new ArrayList<>();
                    p.iterator().forEachRemaining(p2 -> list.add(p2.toString()));
                    assert list.remove(0).equals("modules");

                    if (!list.get(list.size() - 1).equals("module-info.class")) {
                        list.remove(0);
                    }

                    String outPath = String.join("/", list);
                    ZipEntry ze = new ZipEntry(outPath);
                    zipStream.putNextEntry(ze);
                    zipStream.write(data);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            });
        }
    }
}