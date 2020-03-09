/*
 * ProGuard -- shrinking, optimization, obfuscation, and preverification
 *             of Java bytecode.
 *
 * Copyright (c) 2002-2019 GuardSquare NV
 */
package proguard.tools;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import proguard.classfile.ClassPool;
import proguard.classfile.attribute.Attribute;
import proguard.classfile.attribute.annotation.visitor.AllAnnotationVisitor;
import proguard.classfile.attribute.annotation.visitor.AnnotationTypeFilter;
import proguard.classfile.attribute.visitor.AllAttributeVisitor;
import proguard.classfile.attribute.visitor.AttributeNameFilter;
import proguard.classfile.kotlin.KotlinConstants;
import proguard.classfile.kotlin.visitor.MultiKotlinMetadataVisitor;
import proguard.classfile.kotlin.visitor.ReferencedKotlinMetadataVisitor;
import proguard.classfile.util.ClassUtil;
import proguard.classfile.util.WarningPrinter;
import proguard.classfile.util.kotlin.KotlinMetadataInitializer;
import proguard.classfile.visitor.ClassCounter;
import proguard.classfile.visitor.ClassPoolFiller;
import proguard.classfile.visitor.ClassPrinter;
import proguard.classfile.visitor.MultiClassVisitor;
import proguard.io.*;
import proguard.resources.file.ResourceFilePool;
import proguard.resources.file.visitor.ResourceFilePoolFiller;
import proguard.resources.kotlinmodule.io.KotlinModuleDataEntryReader;
import proguard.resources.kotlinmodule.visitor.KotlinModulePrinter;
import proguard.util.ExtensionMatcher;
import proguard.util.OrMatcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;


/**
 * Command-line tool to print Kotlin metadata.
 *
 * @author James Hamilton
 */
@Command(name                 = "kotlin-metadata-printer",
         description          = "\nThe Kotlin metadata printer is a free tool to print the Kotlin metadata in a human-readable format. The printer is " +
                                "built on the ProGuard Core library. The tool can process class files, zip files, jars or apks.",
         parameterListHeading = "%nParameters:%n",
         optionListHeading    = "%nOptions:%n",
         header               = "\nKotlin metadata printer, built on the ProGuard Core library.\n",
         footer               = "\nCopyright (c) 2002-2020 Guardsquare NV.")
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
                       new proguard.classfile.kotlin.visitor.KotlinMetadataPrinter(outPrinter, ""))));
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
                classReader =
                    new NameFilteredDataEntryReader("META-INF/*.kotlin_module",
                    new KotlinModuleDataEntryReader(
                        new ResourceFilePoolFiller(resourceFilePool)),
                    classReader);
            }

            // Extract files from an archive if necessary.
            classReader =
                    new FilteredDataEntryReader(
                    new DataEntryNameFilter(new OrMatcher(
                                            new ExtensionMatcher("jar"),
                                            new ExtensionMatcher("zip"),
                                            new ExtensionMatcher("apk"))),
                        new JarReader(classReader),
                    classReader);

            // Parse all classes from the input and fill the classpool.
            (new FileSource(inputFile)).pumpDataEntries(classReader);

            if (printMetadata)
            {
                // Initialize the Kotlin metadata.
                programClassPool.classesAccept(
                        new AllAttributeVisitor(
                        new AttributeNameFilter(Attribute.RUNTIME_VISIBLE_ANNOTATIONS,
                        new AllAnnotationVisitor(
                        new AnnotationTypeFilter(KotlinConstants.TYPE_KOTLIN_METADATA,
                        new KotlinMetadataInitializer(new WarningPrinter(
                                                      new PrintWriter(System.out))))))));

                // Run the Kotlin printer on the classes.
                programClassPool.classesAccept(internalClassNameFilter, kotlinPrinter);
            }

            if (printModule)
            {
                resourceFilePool.resourceFilesAccept(new KotlinModulePrinter(outPrinter, ""));
            }

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

    public static void main(String[] args)
    {
        CommandLine.run(new KotlinMetadataPrinter(), System.out, args);
    }
}
