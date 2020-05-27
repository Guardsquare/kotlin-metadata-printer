/*
 * Kotlin metadata printer -- tool to display the Kotlin metadata
 * from Java class files.
 *
 * Copyright (c) 2002-2020 Guardsquare NV
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package proguard.io;

import com.googlecode.d2j.reader.DexFileReader;

import com.googlecode.d2j.dex.Dex2jar;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * A DataEntryReader that reads dex files, converts the classes to Java bytecode
 * and delegates the reading of the converted classes to another reader.
 *
 * @author Thomas Neidhart
 */
public class Dex2JarReader implements DataEntryReader
{
    private final boolean         readCode;
    private final DataEntryReader dataEntryReader;


    /**
     * Creates a new Dex2JarReader.
     */
    public Dex2JarReader(boolean         readCode,
                         DataEntryReader dataEntryReader)
    {
        this.readCode        = readCode;
        this.dataEntryReader = dataEntryReader;
    }

    // Implementation for DataEntryReader.

    @Override
    public void read(DataEntry dataEntry) throws IOException
    {
        // Create a temporary directory for the classes.
        final Path          tempDirectory = Files.createTempDirectory(dataEntry.getName());
        final InputStream   inputStream   = dataEntry.getInputStream();
        final DexFileReader reader        = new DexFileReader(inputStream);

        Dex2jar.from(reader)
            .skipDebug(!readCode)
            .printIR(false)
            .noCode(!readCode)
            .to(tempDirectory);

        // Delegate to a directory source.
        final DirectorySource directorySource = new DirectorySource(tempDirectory.toFile());
        directorySource.pumpDataEntries(dataEntryReader);

        // Clean up.
        try (Stream<Path> walk = Files.walk(tempDirectory))
        {
            walk.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }
}
