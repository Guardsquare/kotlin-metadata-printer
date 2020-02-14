/*
 * ProGuard -- shrinking, optimization, obfuscation, and preverification
 *             of Java bytecode.
 *
 * Copyright (c) 2002-2019 GuardSquare NV
 */
package proguard.tools;

import picocli.CommandLine;
import picocli.CommandLine.*;
import proguard.classfile.*;
import proguard.classfile.attribute.annotation.visitor.AllAnnotationVisitor;
import proguard.classfile.attribute.annotation.visitor.AnnotationTypeFilter;
import proguard.classfile.attribute.visitor.AllAttributeVisitor;
import proguard.classfile.attribute.visitor.AttributeNameFilter;
import proguard.classfile.kotlin.*;
import proguard.classfile.kotlin.visitors.MultiKotlinMetadataVisitor;
import proguard.classfile.util.ClassReferenceInitializer;
import proguard.classfile.util.ClassSuperHierarchyInitializer;
import proguard.classfile.util.ClassUtil;
import proguard.classfile.util.WarningPrinter;
import proguard.classfile.visitor.*;
import proguard.io.*;
import proguard.resources.file.ResourceFilePool;
import proguard.util.ExtensionMatcher;
import proguard.util.OrMatcher;

import java.io.*;
import java.util.Collections;


/**
 * Command-line tool to print Kotlin metadata.
 *
 * @author James Hamilton
 */
@Command(name                 = "kotlin-metadata-printer",
         description          = "Print Kotlin Metadata",
         parameterListHeading = "%nParameters:%n",
         optionListHeading    = "%nOptions:%n",
         header               = "Kotlin metadata printer",
         footer               = "Built on ProGuard")
public class KotlinMetadataPrinter implements Runnable
{
    @SuppressWarnings("CanBeFinal")
    @Option(names = "--filter", description = "class name filter")
    private String classNameFilter = null;

    @SuppressWarnings("CanBeFinal")
    @Option(names = "--printmodule", description = "print Kotlin module information")
    private boolean printModule = false;

    @SuppressWarnings("CanBeFinal")
    @Option(names = "--printclass", description = "print corresponding class")
    private boolean printClass = false;

    @SuppressWarnings("CanBeFinal")
    @Option(names = "--printmetadata", description = "print metadata")
    private boolean printMetadata = false;

    @SuppressWarnings("unused")
    @Parameters(index = "0", arity = "1", paramLabel = "inputfile", description = "inputfile to process (*.apk|.jar|zip|class)")
    private File inputFilename;

    @SuppressWarnings("unused")
    @Option(names = "--output", description = "write output to this file instead of stdout")
    private File outputFile;

    private int kotlinMetadataCount = 0;

    public void run()
    {
        printMetadata = (!printModule && !printClass && !printMetadata) || printMetadata || printClass;

        try
        {
            // Local variables.
            File                   inputFile              = new File(inputFilename.getAbsolutePath());
            ClassPool              programClassPool       = new ClassPool();
            ResourceFilePool       resourceFilePool       = new ResourceFilePool();

            FileOutputStream      outputFileOutputStream = null;
            PrintWriter           outPrinter;

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
            MultiClassVisitor kotlinPrinter = new MultiClassVisitor(classCounter);

            if (printMetadata)
            {
                kotlinPrinter.addClassVisitor(
                   new ReferencedKotlinMetadataVisitor(
                   new MultiKotlinMetadataVisitor(
                       (clazz, kotlinMetadata) -> kotlinMetadataCount++,
                       new proguard.classfile.kotlin.KotlinMetadataPrinter(outPrinter))));
            }

            if (printClass)
            {
                kotlinPrinter.addClassVisitor(new ClassPrinter(outPrinter));
            }

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
                new Dex2JarReader(printClass,
                    classReader),
                classReader);

            if (printModule)
            {
/*                classReader =
                    new NameFilteredDataEntryReader("META-INF/*.kotlin_module",
                    new KotlinModuleDataEntryReader(
                        new ResourceFilePoolFiller(resourceFilePool)),
                    classReader);*/
            }

            // Extract files from an archive if necessary.
            classReader =
                    new FilteredDataEntryReader(
                    new DataEntryNameFilter(new OrMatcher(new ExtensionMatcher("jar"), new ExtensionMatcher("zip"), new ExtensionMatcher("apk"))),
                        new JarReader(classReader),
                    classReader);

            // Parse all classes from the input and fill the classpool.
            (new FileSource(inputFile)).pumpDataEntries(classReader);

            // Initialize the classes in the class pool.
            initialize(programClassPool, new ClassPool());

            // Run the Kotlin printer on the classes.
            programClassPool.classesAccept(internalClassNameFilter, kotlinPrinter);

            if (classCounter.getCount() == 0)
            {
                System.err.println("No classes found");
            }
            else if (printMetadata && kotlinMetadataCount == 0)
            {
                System.err.println("No Kotlin metadata found in " + classCounter.getCount() + " classes");
            }

            if (outputFileOutputStream != null)
            {
                outputFileOutputStream.close();
            }
        }
        catch (IOException e)
        {
            System.err.println("Failed printing Kotlin metadata: " + e.getMessage());
        }
    }

    /**
     * Initializes the cached cross-references of the classes in the given
     * class pools.
     *
     * @param programClassPool the program class pool, typically with processed
     *                         classes.
     * @param libraryClassPool the library class pool, typically with run-time
     *                         classes.
     */
    public static void initialize(ClassPool programClassPool,
                                  ClassPool libraryClassPool)
    {
        // We hide the warnings about missing dependencies, as we just want to print the metadata.
        PrintWriter    printWriter    = new PrintWriter(System.err);
        WarningPrinter warningPrinter = new WarningPrinter(printWriter, Collections.singletonList("**"));

        // Initialize the class hierarchies.
        libraryClassPool.classesAccept(
            new ClassSuperHierarchyInitializer(programClassPool,
                                               libraryClassPool,
                                               null,
                                               null));

        programClassPool.classesAccept(
            new ClassSuperHierarchyInitializer(programClassPool,
                                               libraryClassPool,
                                               warningPrinter,
                                               warningPrinter));

        ClassVisitor kotlinMetadataInitializer =
            new AllAttributeVisitor(
            new AttributeNameFilter("RuntimeVisibleAnnotations",
            new AllAnnotationVisitor(
            new AnnotationTypeFilter(KotlinConstants.TYPE_KOTLIN_METADATA,
                                     new KotlinMetadataInitializer()))));

        programClassPool.classesAccept(kotlinMetadataInitializer);
        libraryClassPool.classesAccept(kotlinMetadataInitializer);

        // Initialize the other references from the program classes.
        programClassPool.classesAccept(
            new ClassReferenceInitializer(programClassPool,
                                          libraryClassPool,
                                          warningPrinter,
                                          warningPrinter,
                                          warningPrinter,
                                          null));

        // Flush the warnings.
        printWriter.flush();
    }

    public static void main(String[] args)
    {
        CommandLine.run(new KotlinMetadataPrinter(), System.out, args);
    }
}
