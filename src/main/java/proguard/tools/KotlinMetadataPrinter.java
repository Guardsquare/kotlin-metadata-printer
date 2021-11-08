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
package proguard.tools;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.json.JSONObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import proguard.classfile.ClassPool;
import proguard.classfile.Clazz;
import proguard.classfile.attribute.Attribute;
import proguard.classfile.attribute.annotation.visitor.AllAnnotationVisitor;
import proguard.classfile.attribute.annotation.visitor.AnnotationTypeFilter;
import proguard.classfile.attribute.visitor.AllAttributeVisitor;
import proguard.classfile.attribute.visitor.AttributeNameFilter;
import proguard.classfile.kotlin.*;
import proguard.classfile.kotlin.visitor.AllFunctionVisitor;
import proguard.classfile.kotlin.visitor.KotlinFunctionVisitor;
import proguard.classfile.kotlin.visitor.MultiKotlinMetadataVisitor;
import proguard.classfile.kotlin.visitor.ReferencedKotlinMetadataVisitor;
import proguard.classfile.util.ClassReferenceInitializer;
import proguard.classfile.util.ClassUtil;
import proguard.classfile.util.WarningPrinter;
import proguard.classfile.util.kotlin.KotlinMetadataInitializer;
import proguard.classfile.visitor.*;
import proguard.io.*;
import proguard.kotlin.printer.KotlinSourcePrinter;
import proguard.util.ExtensionMatcher;
import proguard.util.OrMatcher;

import java.io.*;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Command-line tool to print Kotlin metadata.
 *
 * @author James Hamilton
 */
@Command(name                 = "kotlin-metadata-printer",
         description          = "\nThe Kotlin metadata printer is a free tool to print the Kotlin metadata in a human-readable format. The printer is " +
                                "built on the ProGuard Core library. The tool can process class files, zip files, jars, apks or aars.",
         parameterListHeading = "%nParameters:%n",
         optionListHeading    = "%nOptions:%n",
         header               = "\nKotlin metadata printer, built on the ProGuard Core library.\n",
         footer               = "\nCopyright (c) 2002-2020 Guardsquare NV.")
public class KotlinMetadataPrinter
implements   Runnable
{
    @SuppressWarnings("FieldMayBeFinal")
    @Option(names = "--filter", description = "class name filter")
    private String classNameFilter = null;

    @SuppressWarnings("unused")
    @Parameters(index = "0", arity = "1", paramLabel = "inputfile", description = "inputfile to process (*.apk|aar|jar|zip|class)")
    private File inputFilename;

    @SuppressWarnings("unused")
    @Option(names = "--output", description = "write output to this file instead of stdout")
    private File outputFile;

    @SuppressWarnings("FieldMayBeFinal")
    @Option(names = "--json", description = "Output JSON")
    private boolean json = false;

    @SuppressWarnings("FieldMayBeFinal")
    @Option(arity = "1", names = "--divider", description = "string to print in between Kotlin metadata items")
    private String  divider = "/* ------------------------------------------------- */\n";

    private int kotlinMetadataCount = 0;


    public void run()
    {
        try
        {
            // Local variables.
            File             inputFile              = new File(inputFilename.getAbsolutePath());
            ClassPool        programClassPool       = new ClassPool();
            FileOutputStream outputFileOutputStream = null;
            PrintWriter      outPrinter;


            // Construct printer.
            // ------------------
            if (outputFile != null)
            {
                outputFileOutputStream = new FileOutputStream(outputFile);
                outPrinter             = new PrintWriter(outputFileOutputStream, true);
            }
            else
            {
                outPrinter = new PrintWriter(System.out, true);
            }

            ClassCounter      classCounter  = new ClassCounter();
            MultiClassVisitor kotlinPrinter = new MultiClassVisitor(
                 classCounter,
                 new ReferencedKotlinMetadataVisitor(
                 new MultiKotlinMetadataVisitor(
                     (clazz, kotlinMetadata) -> kotlinMetadataCount++,
                     new KotlinSourcePrinter(programClassPool))));

            // Construct reader.
            // -----------------
            String internalClassNameFilter = classNameFilter == null ? "**" :
                                             ClassUtil.internalClassName(classNameFilter);

            DataEntryReader classReader =
                new NameFilteredDataEntryReader("**.class",
                new ClassReader(false, false, false, false, null,
                new ClassPoolFiller(programClassPool)));

            // Convert dex files to a JAR first.
            classReader =
                new NameFilteredDataEntryReader("classes*.dex",
                new Dex2JarReader(false,
                    classReader),
                classReader);

            // Extract files from an archive if necessary.
            classReader =
                    new FilteredDataEntryReader(
                    new DataEntryNameFilter(new ExtensionMatcher("aar")),
                        new JarReader(
                        new NameFilteredDataEntryReader("classes.jar",
                        new JarReader(classReader))),
                    new FilteredDataEntryReader(
                    new DataEntryNameFilter(new OrMatcher(
                                            new ExtensionMatcher("jar"),
                                            new ExtensionMatcher("zip"),
                                            new ExtensionMatcher("apk"))),
                        new JarReader(classReader),
                    classReader));

            // Parse all classes from the input and fill the classpool.
            DataEntryReader finalClassReader = classReader;
            (new FileSource(inputFile)).pumpDataEntries(dataEntry -> {
                try {
                    finalClassReader.read(dataEntry);
                } catch (Exception ignored) {}
            });

            initialize(programClassPool);
            // Run the Kotlin printer on the classes.
            programClassPool.classesAccept(internalClassNameFilter, kotlinPrinter);

            if (json)
            {
                JSONObject rootObject               = new JSONObject();
                JSONObject jsonMetadataList         = new JSONObject();
                JSONObject statistics               = new JSONObject();
                JSONObject javaStatistics           = new JSONObject();
                JSONObject kotlinStatistics         = new JSONObject();
                JSONObject kotlinMetadataStatistics = new JSONObject();
                kotlinMetadataStatistics.put(metadataKindToString(KotlinConstants.METADATA_KIND_CLASS),                   0);
                kotlinMetadataStatistics.put(metadataKindToString(KotlinConstants.METADATA_KIND_FILE_FACADE),             0);
                kotlinMetadataStatistics.put(metadataKindToString(KotlinConstants.METADATA_KIND_SYNTHETIC_CLASS),         0);
                kotlinMetadataStatistics.put(metadataKindToString(KotlinConstants.METADATA_KIND_MULTI_FILE_CLASS_FACADE), 0);
                kotlinMetadataStatistics.put(metadataKindToString(KotlinConstants.METADATA_KIND_MULTI_FILE_CLASS_PART),   0);
                JSONObject kotlinFunctionStatistics = new JSONObject();
                kotlinFunctionStatistics.put("normal",    0);
                kotlinFunctionStatistics.put("synthetic", 0);

                programClassPool.classesAccept(internalClassNameFilter,
                    new MultiClassVisitor(
                    // Build the JSON object.
                    new ClassProcessingInfoFilter(Objects::nonNull,
                    new ReferencedKotlinMetadataVisitor(
                    (clazz, kotlinMetadata) -> {
                        JSONObject metadata = new JSONObject();
                        metadata.put("package",    ClassUtil.externalClassName(ClassUtil.internalPackageName(clazz.getName())));
                        metadata.put("name",       ClassUtil.externalShortClassName(ClassUtil.internalShortClassName(clazz.getName())));
                        metadata.put("kind",       metadataKindToString(kotlinMetadata.k));
                        metadata.put("printed",    clazz.getProcessingInfo());
                        metadata.put("intrinsics", Collections.EMPTY_MAP); // TODO(#1929)
                        jsonMetadataList.put(ClassUtil.externalClassName(clazz.getName()), metadata);
                    })),

                    // Collect statistics.
                    new ReferencedKotlinMetadataVisitor(
                    new MultiKotlinMetadataVisitor(
                    new AllFunctionVisitor(
                    new KotlinFunctionVisitor() {
                        @Override
                        public void visitFunction(Clazz clazz, KotlinDeclarationContainerMetadata kotlinDeclarationContainerMetadata, KotlinFunctionMetadata kotlinFunctionMetadata) {
                            kotlinFunctionStatistics.increment("normal");
                        }

                        @Override
                        public void visitSyntheticFunction(Clazz clazz, KotlinSyntheticClassKindMetadata kotlinSyntheticClassKindMetadata, KotlinFunctionMetadata kotlinFunctionMetadata) {
                            kotlinFunctionStatistics.increment("synthetic");
                        }

                        @Override
                        public void visitAnyFunction(Clazz clazz, KotlinMetadata kotlinMetadata, KotlinFunctionMetadata kotlinFunctionMetadata) { }
                    }),

                    (_clazz, kotlinMetadata) -> kotlinMetadataStatistics.increment(metadataKindToString(kotlinMetadata.k))
                ))));

                statistics.put("java",       javaStatistics.put("classes", classCounter.getCount()));
                statistics.put("kotlin",     kotlinStatistics.put("metadata", kotlinMetadataStatistics).put("functions", kotlinFunctionStatistics));
                rootObject.put("input",      inputFile.getName());
                rootObject.put("statistics", statistics);
                rootObject.put("metadata",   jsonMetadataList);

                outPrinter.println(rootObject.toString(3));
            }
            else
            {
                AtomicBoolean first = new AtomicBoolean(true);
                programClassPool.classesAccept(
                    new ClassProcessingInfoFilter(Objects::nonNull,
                    clazz -> {
                        if (!first.get())
                        {
                            outPrinter.println(divider);
                        }
                        first.set(false);
                        String code = (String) clazz.getProcessingInfo();
                        outPrinter.print(code);
                    }));
            }

            outPrinter.flush();

            if (!json)
            {
                if (classCounter.getCount() == 0)
                {
                    System.out.println("No classes found");
                }
                else if (kotlinMetadataCount == 0)
                {
                    System.out.println("No Kotlin metadata found in " + classCounter.getCount() + " classes");
                }
            }

            if (outputFileOutputStream != null)
            {
                outputFileOutputStream.close();
            }
        }
        catch (Exception e)
        {
            System.err.println("Failed printing Kotlin metadata: " + e.getMessage());
            e.printStackTrace();
            System.exit(-1);
        }
    }

    /**
     * Initializes the cached cross-references of the classes in the given
     * class pools.
     * @param programClassPool the program class pool.
     */
    public static void initialize(ClassPool programClassPool)
    {
        WarningPrinter nullWarningPrinter = new WarningPrinter(new PrintWriter(
                new OutputStream() {
                    @Override
                    public void write(int b) { }
                }
        ));

        // Initialize the Kotlin metadata.
        programClassPool.classesAccept(new KotlinMetadataInitializer((clazz, message) -> { }));

        // Initialize the other references from the program classes.
        programClassPool.classesAccept(
            new ClassReferenceInitializer(programClassPool,
                                          new ClassPool(),
                                          nullWarningPrinter,
                                          null,
                                          null,
                                          null));
    }

    private static String metadataKindToString(int k)
    {
        String metadataKindString = KotlinConstants.metadataKindToString(k);
        return metadataKindString.substring(0, 1).toLowerCase() +
               StringUtils.remove(StringUtils.remove(WordUtils.capitalize(metadataKindString, '-', ' '), '-'), ' ').substring(1);
    }

    public static void main(String[] args)
    {
        CommandLine.run(new KotlinMetadataPrinter(), System.out, args);
    }
}
