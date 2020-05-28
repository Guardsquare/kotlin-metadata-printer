/*
 * ProGuard -- shrinking, optimization, obfuscation, and preverification
 *             of Java bytecode.
 *
 * Copyright (c) 2002-2020 Guardsquare NV
 */
package proguard.kotlin.printer;

import kotlinx.metadata.KmAnnotation;
import kotlinx.metadata.KmAnnotationArgument;
import kotlinx.metadata.KmVariance;
import proguard.classfile.*;
import proguard.classfile.attribute.annotation.Annotation;
import proguard.classfile.attribute.annotation.visitor.AllAnnotationVisitor;
import proguard.classfile.attribute.annotation.visitor.AnnotationTypeFilter;
import proguard.classfile.attribute.annotation.visitor.AnnotationVisitor;
import proguard.classfile.attribute.visitor.AllAttributeVisitor;
import proguard.classfile.attribute.visitor.AttributeConstantVisitor;
import proguard.classfile.kotlin.*;
import proguard.classfile.kotlin.flags.*;
import proguard.classfile.kotlin.visitor.*;
import proguard.classfile.kotlin.visitor.filter.*;
import proguard.classfile.util.ClassUtil;
import proguard.classfile.visitor.*;
import proguard.kotlin.printer.visitor.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.System.*;
import static java.util.Objects.*;
import static proguard.classfile.kotlin.KotlinConstants.*;
import static proguard.classfile.util.ClassUtil.externalClassName;
import static proguard.classfile.util.ClassUtil.externalShortClassName;

/**
 * Prints the Kotlin metadata annotation in a format similar to
 * Kotlin source code.
 *
 * The printed metadata is written to the class processingInfo field.
 *
 * @author James Hamilton
 */
public class KotlinSourcePrinter
implements   KotlinMetadataVisitor
{
    private static final String  INDENTATION         = "    ";
    private static final Pattern FUNCTION_TYPE_REGEX = Pattern.compile("^kotlin/Function(?<paramCount>\\d+)$");
    private static final int MAX_ENUM_ENTRY_PER_LINE = 5;

    private final ClassPool programClassPool;

    private final Stack<StringBuilder>          stringBuilders = new Stack<>();
    private final MyKotlinSourceMetadataPrinter printer        = new MyKotlinSourceMetadataPrinter();
    private final Set<Clazz>                    alreadyVisited = new HashSet<>();
    private       int                           indentation;
    private       Context                       context;


    public KotlinSourcePrinter(ClassPool programClassPool)
    {
        this.programClassPool = programClassPool;
    }

    @Override
    public void visitAnyKotlinMetadata(Clazz clazz, KotlinMetadata kotlinMetadata)
    {
        if (context == null)
        {
            this.context = new Context();
        }
        pushStringBuilder();
        // Inner printer gets executed, then the string is written to the visitorInfo.
        // We only print non-synthetic classes directly; synthetic classes will be
        // printed within non-synthetic ones. Likewise, multi-file class parts will
        // be printed in their facades.
        kotlinMetadata.accept(clazz,
            new KotlinMetadataFilter(
            _kotlinMetadata -> _kotlinMetadata.k != METADATA_KIND_SYNTHETIC_CLASS &&
                               _kotlinMetadata.k != METADATA_KIND_MULTI_FILE_CLASS_PART,
            KotlinSourcePrinter.this.printer));

        String result = popStringBuilder();
        if (result.length() > 0)
        {
            clazz.setProcessingInfo(result);
        }
        else
        {
            clazz.setProcessingInfo(null);
        }
    }

    public Context getContext()
    {
        return this.context;
    }


    /**
     * The main printer class implements all the Kotlin visitors, each
     * printing their repesective part.
     */
    private class MyKotlinSourceMetadataPrinter
    implements    KotlinMetadataVisitor,
                  KotlinConstructorVisitor,
                  KotlinTypeParameterVisitor,
                  KotlinTypeVisitor,
                  KotlinValueParameterVisitor,
                  KotlinFunctionVisitor,
                  KotlinTypeAliasVisitor,
                  KotlinPropertyVisitor,
                  KotlinAnnotationVisitor,
                  KotlinVersionRequirementVisitor
    {

        // Implementations for KotlinMetadataVisitor

        @Override
        public void visitAnyKotlinMetadata(Clazz clazz, KotlinMetadata kotlinMetadata) { }

        @Override
        public void visitKotlinDeclarationContainerMetadata(Clazz                              clazz,
                                                            KotlinDeclarationContainerMetadata kotlinDeclarationContainerMetadata)
        {
            if (kotlinDeclarationContainerMetadata.typeAliases.size() > 0)
            {
                println();
                println("// Type aliases", true);
                println();
                kotlinDeclarationContainerMetadata.typeAliasesAccept(clazz, this);
            }

            if (kotlinDeclarationContainerMetadata.properties.size()               > 0 ||
                kotlinDeclarationContainerMetadata.localDelegatedProperties.size() > 0)
            {
                println();
                println("// Properties", true);
                println();
                kotlinDeclarationContainerMetadata.propertiesAccept(         clazz, this);
                kotlinDeclarationContainerMetadata.delegatedPropertiesAccept(clazz, this);
            }

            if (kotlinDeclarationContainerMetadata.functions.size() > 0)
            {
                println();
                println("// Functions", true);
                println();
                kotlinDeclarationContainerMetadata.functionsAccept(clazz, this);
            }
        }


        @Override
        public void visitKotlinClassMetadata(Clazz clazz, KotlinClassKindMetadata kotlinClassKindMetadata)
        {
            if (alreadyVisited.contains(clazz)) {
                return;
            }
            alreadyVisited.add(clazz);
            context.push(new ContextFrame(clazz, kotlinClassKindMetadata));

            printHeader(clazz, kotlinClassKindMetadata);

            clazz.attributesAccept(new AnnotationPrinter(KotlinSourcePrinter.this));

            kotlinClassKindMetadata.versionRequirementAccept(clazz,
                (_clazz, versionRequirement) -> printVersionRequirement(versionRequirement));

            String className = context.className(clazz, "_");

            if (kotlinClassKindMetadata.flags.isCompanionObject && className.equals("Companion"))
            {
                // Trim the space from the end of the "companion object " string and don't print Companion.
                print(classFlags(kotlinClassKindMetadata.flags).trim(), true);
            }
            else
            {
                print(classFlags(kotlinClassKindMetadata.flags) + className, true);
            }

            kotlinClassKindMetadata.accept(clazz,
                new MultiKotlinMetadataVisitor(
                    // First load the type parameters into the context.
                    new AllTypeParameterVisitor(context),
                    // Including the type parameters declared in the anonymous object origin location hierarchy.
                    new KotlinClassToAnonymousObjectOriginClassVisitor(programClassPool,
                        new MultiKotlinMetadataVisitor(
                        new KotlinClassToInlineOriginFunctionVisitor(kotlinClassKindMetadata.anonymousObjectOriginName,
                            new AllTypeParameterVisitor(context)),
                        new AllTypeParameterVisitor(context))),
                    // Then print them.
                    new AllTypeParameterVisitor(
                    new KotlinTypeParameterVisitorWrapper(
                        (i, kotlinTypeParameterMetadata) -> print(i == 0 ? "<" : ", "),
                        MyKotlinSourceMetadataPrinter.this,
                        (i, kotlinTypeParameterMetadata) -> print(i == kotlinClassKindMetadata.typeParameters.size() - 1 ? ">" : "")))));

            pushStringBuilder();
            kotlinClassKindMetadata.constructorsAccept(clazz,
                new KotlinConstructorFilter(
                    constructor -> constructor.flags.isPrimary && !constructor.isParameterless(),
                    MyKotlinSourceMetadataPrinter.this));
            String primaryConstructorString = popStringBuilder();

            // Print an extra space if there was no constructor string generated.
            print(primaryConstructorString.length() == 0 ? " " : primaryConstructorString);

            kotlinClassKindMetadata.superTypesAccept(clazz,
                new KotlinTypeFilter(
                    (kotlinTypeMetadata) -> !kotlinTypeMetadata.className.equals(NAME_KOTLIN_ANY) &&
                                            !kotlinTypeMetadata.className.equals(NAME_KOTLIN_ENUM),
                    new KotlinTypeVisitorWrapper(
                        (i, kotlinTypeMetadata) -> print(i == 0 ? ": " : ", "),
                        MyKotlinSourceMetadataPrinter.this,
                        (i, kotlinTypeMetadata) -> print(i == kotlinClassKindMetadata.superTypes.size() - 1 ? " " : ""))));

            indent();

            pushStringBuilder();

            ClassCounter classCounter = new ClassCounter();
            kotlinClassKindMetadata.sealedSubclassesAccept(
                new MultiClassVisitor(_clazz -> {
                    if (classCounter.getCount() == 0)
                    {
                        print("// Sealed subclasses: " , true);
                    }
                    else if (classCounter.getCount() > 0)
                    {
                        print(", ");
                    }
                    print(context.className(_clazz.getName(), "."));
                    if (classCounter.getCount() == kotlinClassKindMetadata.sealedSubclassNames.size() - 1)
                    {
                        println();
                    }
                }, classCounter));

            if (kotlinClassKindMetadata.constructors.size() > 1)
            {
                println("// Secondary constructors", true);
                kotlinClassKindMetadata.constructorsAccept(clazz,
                    new KotlinConstructorFilter(constructor -> !constructor.flags.isPrimary,
                        MyKotlinSourceMetadataPrinter.this));
            }

            visitKotlinDeclarationContainerMetadata(clazz, kotlinClassKindMetadata);

            // Companion is also a nested class but print it here first, so it's not mixed in with the nested classes.
            kotlinClassKindMetadata.companionAccept((companionClazz, companionMetadata) -> {
                println();
                companionMetadata.accept(companionClazz, MyKotlinSourceMetadataPrinter.this);
            });

            kotlinClassKindMetadata.nestedClassesAccept(false,
                new ReferencedKotlinMetadataVisitor(
                new KotlinMetadataVisitorWrapper(
                    (i, metadata) -> {
                        if (i == 0)
                        {
                            println();
                            println("// Nested subclasses", true);
                            println();
                        }
                    },
                    MyKotlinSourceMetadataPrinter.this)));

            visitChildClasses(clazz);

            pushStringBuilder();
            for (int i = 0; i < kotlinClassKindMetadata.enumEntryNames.size(); i++)
            {
                if (i > 0 && i % MAX_ENUM_ENTRY_PER_LINE == 0)
                {
                    println();
                }
                print(kotlinClassKindMetadata.enumEntryNames.get(i), i % MAX_ENUM_ENTRY_PER_LINE == 0);
                print(i != kotlinClassKindMetadata.enumEntryNames.size() - 1 ? ", " : "");
            }

            outdent();

            String enumEntriesString = popStringBuilder();
            String classBody         = popStringBuilder();

            if (classBody.length() > 0)
            {
                println("{");
                if (enumEntriesString.length() > 0)
                {
                    println(enumEntriesString + ";");
                }
                print(classBody, true);
                println("}", true);
            }
            else if (enumEntriesString.length() > 0)
            {
                // Enums without bodies.
                boolean oneLine = kotlinClassKindMetadata.enumEntryNames.size() <= MAX_ENUM_ENTRY_PER_LINE;
                print("{" + (oneLine ? " " : lineSeparator()));
                print(oneLine ? enumEntriesString.trim() + " " : enumEntriesString + lineSeparator(), !oneLine);
                println("}", !oneLine);
            }
            else
            {
                println();
                println();
            }

            context.pop();
        }



        @Override
        public void visitKotlinFileFacadeMetadata(Clazz clazz, KotlinFileFacadeKindMetadata kotlinFileFacadeKindMetadata)
        {
            context.push(new ContextFrame(clazz, kotlinFileFacadeKindMetadata));
            printHeader(clazz, kotlinFileFacadeKindMetadata);
            visitKotlinDeclarationContainerMetadata(clazz, kotlinFileFacadeKindMetadata);
            visitChildClasses(clazz);
            context.pop();
        }


        @Override
        public void visitKotlinSyntheticClassMetadata(Clazz                            clazz,
                                                      KotlinSyntheticClassKindMetadata kotlinSyntheticClassKindMetadata)
        {
            if (alreadyVisited.contains(clazz)) {
                return;
            }
            alreadyVisited.add(clazz);
            context.push(new ContextFrame(clazz, kotlinSyntheticClassKindMetadata));

            String className = context.className(clazz, "_");
            printHeader(clazz, kotlinSyntheticClassKindMetadata);
            print(
                "/* " + kotlinSyntheticClassKindMetadata.flavor.toString().toLowerCase() + " */ " +
                "class " + className,
                true
            );

            if (kotlinSyntheticClassKindMetadata.functions.size() > 0)
            {
                println(" {");
                indent();
                println("// Functions", true);
                kotlinSyntheticClassKindMetadata.functionsAccept(clazz, this);
                outdent();
                println("}", true);
            }
            else
            {
                println();
            }
            context.pop();
        }

        @Override
        public void visitKotlinMultiFileFacadeMetadata(Clazz                             clazz,
                                                       KotlinMultiFileFacadeKindMetadata kotlinMultiFileFacadeKindMetadata)
        {
            context.push(new ContextFrame(clazz, kotlinMultiFileFacadeKindMetadata));
            printHeader(clazz, kotlinMultiFileFacadeKindMetadata);
            println();
            indent();
            for (String partClassName : kotlinMultiFileFacadeKindMetadata.partClassNames)
            {
                Clazz partClass = programClassPool.getClass(partClassName);
                if (partClass != null)
                {
                    partClass.kotlinMetadataAccept(MyKotlinSourceMetadataPrinter.this);
                    println();
                }
            }
            outdent();
            context.pop();
        }

        @Override
        public void visitKotlinMultiFilePartMetadata(Clazz                           clazz,
                                                     KotlinMultiFilePartKindMetadata kotlinMultiFilePartKindMetadata)
        {
            context.push(new ContextFrame(clazz, kotlinMultiFilePartKindMetadata));
            printHeader(clazz, kotlinMultiFilePartKindMetadata);
            String shortClassName = externalShortClassName(context.className(clazz, "_"));
            String jvmName        = shortClassName.substring(0, shortClassName.indexOf("__")).replaceFirst("Kt$", "");
            println("@file:JvmName(\"" + jvmName + "\")", true);
            println("@file:JvmMultifileClass", true);
            visitKotlinDeclarationContainerMetadata(clazz, kotlinMultiFilePartKindMetadata);
            visitChildClasses(clazz);
            context.pop();
        }

        // Implementations for KotlinConstructorVisitor


        @Override
        public void visitConstructor(Clazz                     clazz,
                                     KotlinClassKindMetadata   kotlinClassKindMetadata,
                                     KotlinConstructorMetadata kotlinConstructorMetadata)
        {
            kotlinConstructorMetadata.versionRequirementAccept(clazz, kotlinClassKindMetadata,
                (_clazz, versionRequirement) -> printVersionRequirement(kotlinConstructorMetadata.flags.isPrimary ? " " : "",
                                                                        versionRequirement,
                                                                        kotlinConstructorMetadata.flags.isPrimary));
            AtomicInteger annotationsCount = new AtomicInteger(0);

            if (kotlinConstructorMetadata.flags.common.hasAnnotations)
            {
                pushStringBuilder();
                Method method = clazz.findMethod(kotlinConstructorMetadata.jvmSignature.getName(),
                                                 kotlinConstructorMetadata.jvmSignature.getDesc());
                if (method != null)
                {
                    method.accept(clazz,
                                  new MultiMemberVisitor(
                                  new AllAttributeVisitor(
                                  new AllAnnotationVisitor(
                                  new AnnotationTypeFilter("!" + KotlinConstants.TYPE_KOTLIN_METADATA,
                                  new AnnotationVisitor() {
                                      @Override
                                      public void visitAnnotation(Clazz clazz, Annotation annotation) {
                                          annotationsCount.incrementAndGet();
                                      }
                                  }))),
                                  new AllAttributeVisitor(
                                  new AnnotationPrinter(KotlinSourcePrinter.this,
                                                        kotlinConstructorMetadata.flags.isPrimary))));
                }
                String annotationsString = popStringBuilder();

                if (annotationsCount.get() > 0)
                {
                    print((kotlinConstructorMetadata.flags.isPrimary ? " " : "") + annotationsString);
                }
            }

            if (!kotlinConstructorMetadata.flags.isPrimary            ||
                 kotlinConstructorMetadata.versionRequirement != null ||
                 annotationsCount.get()                   > 0)
            {
                print(kotlinConstructorMetadata.flags.isPrimary ? " " : "", !kotlinConstructorMetadata.flags.isPrimary);
                print("constructor");
            }
            print("(");
            kotlinConstructorMetadata.valueParametersAccept(clazz, kotlinClassKindMetadata, this);
            print(") ");
            if (!kotlinConstructorMetadata.flags.isPrimary)
            {
                println("{ }");
            }
        }


        // Implementations for KotlinTypeParameterVisitor

        @Override
        public void visitAnyTypeParameter(Clazz clazz, KotlinTypeParameterMetadata kotlinTypeParameterMetadata)
        {
            // Only print in or out (invariant is default).
            if (!KmVariance.INVARIANT.equals(kotlinTypeParameterMetadata.variance))
            {
                print(kotlinTypeParameterMetadata.variance.toString().toLowerCase());
                print(" ");
            }

            print(typeParameterFlags(kotlinTypeParameterMetadata.flags));

            kotlinTypeParameterMetadata.annotationsAccept(clazz, new KotlinAnnotationVisitorWrapper(
                (i, annotation) -> {},
                this,
                (i, annotation) -> print(" ")));

            print(kotlinTypeParameterMetadata.name);

            kotlinTypeParameterMetadata.upperBoundsAccept(clazz, new KotlinTypeVisitorWrapper(
                (i, typeMetadata) -> print(i == 0 ? " : " : ", "),
                MyKotlinSourceMetadataPrinter.this));
        }


        // Implementations for KotlinValueParameterVisitor.

        @Override
        public void visitAnyValueParameter(Clazz clazz, KotlinValueParameterMetadata kotlinValueParameterMetadata)
        {
            print(kotlinValueParameterMetadata.index != 0 ? ", " : "");
            print(valueParameterFlags(kotlinValueParameterMetadata.flags));
            print(kotlinValueParameterMetadata.isVarArg() ? "vararg " : "");
            print(kotlinValueParameterMetadata.parameterName
                      .replaceAll("^<(.*) (\\d+)>$", "p$2") // <anonymous parameter N> -> pN
                      .replaceAll("<set-\\?>", "p0"));      // <set-?> -> p0
            print(": ");
        }


        @Override
        public void visitPropertyValParameter(Clazz                              clazz,
                                              KotlinDeclarationContainerMetadata kotlinDeclarationContainerMetadata,
                                              KotlinPropertyMetadata             kotlinPropertyMetadata,
                                              KotlinValueParameterMetadata       kotlinValueParameterMetadata)
        {
            visitAnyValueParameter(clazz, kotlinValueParameterMetadata);
            pushStringBuilder();
            kotlinValueParameterMetadata.typeAccept(clazz, kotlinDeclarationContainerMetadata, kotlinPropertyMetadata, this);
            print(popStringBuilder());
            if (kotlinValueParameterMetadata.flags.hasDefaultValue)
            {
                print(" = /* default value */");
            }
        }


        @Override
        public void visitFunctionValParameter(Clazz                        clazz,
                                              KotlinMetadata               kotlinMetadata,
                                              KotlinFunctionMetadata       kotlinFunctionMetadata,
                                              KotlinValueParameterMetadata kotlinValueParameterMetadata)
        {
            visitAnyValueParameter(clazz, kotlinValueParameterMetadata);
            pushStringBuilder();
            kotlinValueParameterMetadata.typeAccept(clazz, kotlinMetadata, kotlinFunctionMetadata, this);
            print(popStringBuilder());
            if (kotlinValueParameterMetadata.flags.hasDefaultValue)
            {
                print(" = /* default value */");
            }
        }


        @Override
        public void visitConstructorValParameter(Clazz                        clazz,
                                                 KotlinClassKindMetadata      kotlinClassKindMetadata,
                                                 KotlinConstructorMetadata    kotlinConstructorMetadata,
                                                 KotlinValueParameterMetadata kotlinValueParameterMetadata)
        {
            visitAnyValueParameter(clazz, kotlinValueParameterMetadata);
            pushStringBuilder();
            // typeAccept calls both normal type accept and then varArg type accept, for varArgs.
            kotlinValueParameterMetadata.typeAccept(clazz, kotlinClassKindMetadata, kotlinConstructorMetadata, this);
            print(popStringBuilder());
            if (kotlinValueParameterMetadata.flags.hasDefaultValue)
            {
                print(" = /* default value */");
            }
        }


        @Override
        public void visitConstructorValParamVarArgType(Clazz                              clazz,
                                                       KotlinDeclarationContainerMetadata kotlinDeclarationContainerMetadata,
                                                       KotlinConstructorMetadata          kotlinConstructorMetadata,
                                                       KotlinValueParameterMetadata       kotlinValueParameterMetadata,
                                                       KotlinTypeMetadata                 kotlinTypeMetadata)
        {
            // We rely on the vararg type accept being called AFTER the normal type accept:
            // it means that we can reset the current string builder, to clear the just visited normal type.
            resetStringBuilder();
            visitAnyType(clazz, kotlinTypeMetadata);
        }


        @Override
        public void visitPropertyValParamVarArgType(Clazz                              clazz,
                                                    KotlinDeclarationContainerMetadata kotlinDeclarationContainerMetadata,
                                                    KotlinPropertyMetadata             kotlinPropertyMetadata,
                                                    KotlinValueParameterMetadata       kotlinValueParameterMetadata,
                                                    KotlinTypeMetadata                 kotlinTypeMetadata)
        {
            resetStringBuilder();
            visitAnyType(clazz, kotlinTypeMetadata);
        }


        @Override
        public void visitFunctionValParamVarArgType(Clazz                        clazz,
                                                    KotlinMetadata               kotlinMetadata,
                                                    KotlinFunctionMetadata       kotlinFunctionMetadata,
                                                    KotlinValueParameterMetadata kotlinValueParameterMetadata,
                                                    KotlinTypeMetadata           kotlinTypeMetadata)
        {
            resetStringBuilder();
            visitAnyType(clazz, kotlinTypeMetadata);
        }

        // Implementations for KotlinPropertyVisitor

        @Override
        public void visitAnyProperty(Clazz                              clazz,
                                     KotlinDeclarationContainerMetadata kotlinDeclarationContainerMetadata,
                                     KotlinPropertyMetadata             kotlinPropertyMetadata)
        {

            if (kotlinPropertyMetadata.flags.common.hasAnnotations &&
                kotlinPropertyMetadata.referencedSyntheticMethodForAnnotations != null)
            {
                kotlinPropertyMetadata.referencedSyntheticMethodForAnnotations.accept(kotlinPropertyMetadata.referencedSyntheticMethodClass,
                    new AllAttributeVisitor(
                    new AnnotationPrinter(KotlinSourcePrinter.this)));
            }

            kotlinPropertyMetadata.versionRequirementAccept(clazz, kotlinDeclarationContainerMetadata, this);
            print(propertyFlags(kotlinPropertyMetadata.flags), true);

            kotlinPropertyMetadata.typeParametersAccept(clazz, kotlinDeclarationContainerMetadata, context);
            kotlinPropertyMetadata.typeParametersAccept(clazz, kotlinDeclarationContainerMetadata,
                new KotlinTypeParameterVisitorWrapper(
                    (i, kotlinTypeParameterMetadata) -> print(i == 0 ? "<" : ", "),
                    MyKotlinSourceMetadataPrinter.this,
                    (i, kotlinTypeParameterMetadata) -> print(i == kotlinPropertyMetadata.typeParameters.size() - 1 ? "> " : "")));

            kotlinPropertyMetadata.receiverTypeAccept(clazz, kotlinDeclarationContainerMetadata, this);
            print(kotlinPropertyMetadata.name);

            print(": ");

            kotlinPropertyMetadata.typeAccept(clazz, kotlinDeclarationContainerMetadata, this);

            if (kotlinPropertyMetadata.flags.hasConstant && kotlinPropertyMetadata.backingFieldSignature != null)
            {
                clazz.fieldAccept(kotlinPropertyMetadata.backingFieldSignature.getName(), kotlinPropertyMetadata.backingFieldSignature.getDesc(),
                    new AllAttributeVisitor(
                    new AttributeConstantVisitor(
                    new ConstantToStringVisitor((constant) -> print(" = " + constant)))));
            }

            if (kotlinPropertyMetadata.backingFieldSignature != null)
            {
                indent();
                println();
                print("// backing field: ", true);
                try
                {
                    // If the types are invalid this will throw an exception.
                    print(ClassUtil.externalFullFieldDescription(0,
                            kotlinPropertyMetadata.backingFieldSignature.getName(),
                            kotlinPropertyMetadata.backingFieldSignature.getDesc()));
                }
                catch (IllegalArgumentException e)
                {
                    print("invalid field descriptor: " + kotlinPropertyMetadata.backingFieldSignature.asString());
                }
                outdent();
            }

            if (kotlinPropertyMetadata.flags.hasGetter)
            {
                indent();
                println();
                if (kotlinPropertyMetadata.getterFlags.common.hasAnnotations &&
                    kotlinPropertyMetadata.referencedGetterMethod != null)
                {
                    kotlinPropertyMetadata.referencedGetterMethod.accept(clazz,
                            new AllAttributeVisitor(
                            new AnnotationPrinter(KotlinSourcePrinter.this)));
                }
                print(propertyAccessorFlags(kotlinPropertyMetadata.getterFlags) + "get", true);
                if (kotlinPropertyMetadata.getterSignature != null)
                {
                    if (kotlinPropertyMetadata.referencedGetterMethod != null)
                    {
                        print(" // getter method: ");
                        print(ClassUtil.externalFullMethodDescription(clazz.getName(),
                                kotlinPropertyMetadata.referencedGetterMethod.getAccessFlags(),
                                kotlinPropertyMetadata.referencedGetterMethod.getName(clazz),
                                kotlinPropertyMetadata.referencedGetterMethod.getDescriptor(clazz)));
                    }
                }
                else if (kotlinPropertyMetadata.getterFlags.isDefault)
                {
                    print(" // default getter");
                }
                outdent();
            }

            if (kotlinPropertyMetadata.flags.hasSetter)
            {
                indent();
                println();
                if (kotlinPropertyMetadata.setterFlags.common.hasAnnotations &&
                    kotlinPropertyMetadata.referencedSetterMethod != null)
                {
                    kotlinPropertyMetadata.referencedSetterMethod.accept(clazz,
                            new AllAttributeVisitor(
                            new AnnotationPrinter(KotlinSourcePrinter.this)));
                }
                print(propertyAccessorFlags(kotlinPropertyMetadata.setterFlags) + "set(", true);
                kotlinPropertyMetadata.setterParametersAccept(clazz, kotlinDeclarationContainerMetadata, this);
                print(")");
                if (kotlinPropertyMetadata.setterSignature != null)
                {
                    if (kotlinPropertyMetadata.referencedSetterMethod != null)
                    {
                        print(" // setter method: ");
                        print(ClassUtil.externalFullMethodDescription(clazz.getName(),
                                kotlinPropertyMetadata.referencedSetterMethod.getAccessFlags(),
                                kotlinPropertyMetadata.referencedSetterMethod.getName(clazz),
                                kotlinPropertyMetadata.referencedSetterMethod.getDescriptor(clazz)));
                    }
                }
                else if (kotlinPropertyMetadata.setterFlags.isDefault)
                {
                    print(" // default setter");
                }
                outdent();
            }

            println();
        }


        // Implementations for KotlinTypeVisitor

        @Override
        public void visitAnyType(Clazz clazz, KotlinTypeMetadata kotlinTypeMetadata)
        {
            AtomicBoolean isExtensionFunctionType = new AtomicBoolean(false);
            AtomicReference<String> parameterName = new AtomicReference<>("");

            kotlinTypeMetadata.annotationsAccept(clazz,
                // ExtensionFunctionTypes are marked by an annotation.
                new KotlinAnnotationFilter(annotation -> annotation.kmAnnotation.getClassName().equals(NAME_KOTLIN_EXTENSION_FUNCTION),
                    (_clazz, annotation) -> isExtensionFunctionType.set(true),
                // A function type can optionally include names for the function parameters (for documentation purposes).
                new KotlinAnnotationFilter(annotation -> annotation.kmAnnotation.getClassName().equals(NAME_KOTLIN_PARAMETER_NAME),
                    (_clazz, annotation) -> parameterName.set(annotation.kmAnnotation.getArguments().get("name").getValue().toString()),
                // Else print the annotation.
                new KotlinAnnotationVisitorWrapper(
                    (i, annotation) -> {},
                    this,
                    (i, annotation) -> print(" ")))));

            print(typeFlags(kotlinTypeMetadata.flags));

            if (parameterName.get().length() > 0)
            {
                print(parameterName + ": ");
            }

            if (kotlinTypeMetadata.variance != null && !kotlinTypeMetadata.variance.equals(KmVariance.INVARIANT))
            {
                print(kotlinTypeMetadata.variance.toString().toLowerCase() + " ");
            }

            if (kotlinTypeMetadata.className != null && kotlinTypeMetadata.className.startsWith(NAME_KOTLIN_FUNCTION))
            {
                // Print function types e.g.
                //    Function<R> = () -> R
                //    Function0<R> = () -> R
                //    Function2<A, B, R> = (A, B) -> R
                //    @ExtensionFunctionType Function3<T, A, B, R> = T.(A, B) -> R
                Matcher matcher    = FUNCTION_TYPE_REGEX.matcher(kotlinTypeMetadata.className);
                int     paramCount = matcher.matches() ? Integer.parseInt(matcher.group("paramCount")) : 0;

                // Nullable function types must be wrapped in ()
                if (kotlinTypeMetadata.flags.isNullable)
                {
                    print("(");
                }

                kotlinTypeMetadata.typeArgumentsAccept(clazz,
                    new KotlinTypeVisitorWrapper(
                    (i, _kotlinTypeMetadata) -> {
                        if (i == 0 && !isExtensionFunctionType.get())
                        {
                            print("(");
                        }
                        else if (i == 1 && isExtensionFunctionType.get())
                        {
                            print(".(");
                        }

                        if (i == paramCount)
                        {
                            print(") -> ");
                        }
                        else if (i > 0 && !isExtensionFunctionType.get() ||
                                 i > 1 &&  isExtensionFunctionType.get())
                        {
                            print(", ");
                        }
                    },
                    this));

                if (kotlinTypeMetadata.flags.isNullable)
                {
                    print(")?");
                }
            }
            else
            {
                if (kotlinTypeMetadata.typeParamID >= 0)
                {
                    print(context.getTypeParamName(kotlinTypeMetadata.typeParamID));
                }
                else
                {
                    print(context.className(kotlinTypeMetadata.aliasName != null ?
                                            kotlinTypeMetadata.aliasName : requireNonNull(kotlinTypeMetadata.className), "."));
                }

                if (kotlinTypeMetadata.flags.isNullable)
                {
                    print("?");
                }

                kotlinTypeMetadata.typeArgumentsAccept(clazz,
                    new KotlinTypeVisitorWrapper(
                    (i, _kotlinTypeMetadata) -> print(i == 0 ? "<" : ", "),
                    this,
                    (i, _kotlinTypeMetadata) -> print(i == kotlinTypeMetadata.typeArguments.size() - 1 ? ">" : "")));
            }
        }


        @Override
        public void visitStarProjection(Clazz clazz, KotlinTypeMetadata typeWithStarArg)
        {
            print("*");
        }


        @Override
        public void visitPropertyReceiverType(Clazz                              clazz,
                                              KotlinDeclarationContainerMetadata kotlinDeclarationContainerMetadata,
                                              KotlinPropertyMetadata             kotlinPropertyMetadata,
                                              KotlinTypeMetadata                 kotlinTypeMetadata)
        {
            visitAnyReceiverType(clazz, kotlinTypeMetadata);
        }


        @Override
        public void visitFunctionReceiverType(Clazz                  clazz,
                                              KotlinMetadata         kotlinMetadata,
                                              KotlinFunctionMetadata kotlinFunctionMetadata,
                                              KotlinTypeMetadata     kotlinTypeMetadata)
        {
            visitAnyReceiverType(clazz, kotlinTypeMetadata);
        }


        private void visitAnyReceiverType(Clazz clazz, KotlinTypeMetadata kotlinTypeMetadata)
        {
            boolean isFunctionType = kotlinTypeMetadata.className != null &&
                                     kotlinTypeMetadata.className.startsWith(NAME_KOTLIN_FUNCTION);
            print(isFunctionType ? "(" : "");
            visitAnyType(clazz, kotlinTypeMetadata);
            print(isFunctionType ? ")." : ".");
        }


        @Override
        public void visitFunctionReturnType(Clazz                  clazz,
                                            KotlinMetadata         kotlinMetadata,
                                            KotlinFunctionMetadata kotlinFunctionMetadata,
                                            KotlinTypeMetadata     kotlinTypeMetadata)
        {
            if (!NAME_KOTLIN_UNIT.equals(kotlinTypeMetadata.className)) {
                print(": ");
                visitAnyType(clazz, kotlinTypeMetadata);
            }
        }


        // Implementations for KotlinTypeAliasVisitor.

        @Override
        public void visitTypeAlias(Clazz                              clazz,
                                   KotlinDeclarationContainerMetadata kotlinDeclarationContainerMetadata,
                                   KotlinTypeAliasMetadata            kotlinTypeAliasMetadata)
        {
            kotlinTypeAliasMetadata.annotationsAccept(clazz, new KotlinAnnotationVisitorWrapper(
                (i, annotation) -> print("", true),
                this,
                (i, annotation) -> print(i == kotlinTypeAliasMetadata.annotations.size() - 1 ? lineSeparator() : " ")));

            kotlinTypeAliasMetadata.versionRequirementAccept(clazz, kotlinDeclarationContainerMetadata, this);
            print("typealias " + kotlinTypeAliasMetadata.name, true);
            kotlinTypeAliasMetadata.typeParametersAccept(clazz, kotlinDeclarationContainerMetadata, context);
            kotlinTypeAliasMetadata.typeParametersAccept(clazz, kotlinDeclarationContainerMetadata,
                new KotlinTypeParameterVisitorWrapper(
                    (i, kotlinTypeParameterMetadata) -> print(i == 0 ? "<" : ", "),
                    MyKotlinSourceMetadataPrinter.this,
                    (i, kotlinTypeParameterMetadata) -> print(i == kotlinTypeAliasMetadata.typeParameters.size() - 1 ? ">" : "")));
            print(" = ");
            kotlinTypeAliasMetadata.underlyingTypeAccept(clazz, kotlinDeclarationContainerMetadata, this);
            println();
        }


        // Implementations for KotlinFunctionVisitor.

        @Override
        public void visitAnyFunction(Clazz                  clazz,
                                     KotlinMetadata         kotlinMetadata,
                                     KotlinFunctionMetadata kotlinFunctionMetadata)
        {
            clazz.attributesAccept(new AnnotationPrinter(KotlinSourcePrinter.this));
            kotlinFunctionMetadata.versionRequirementAccept(clazz, kotlinMetadata, this);
            print(functionFlags(kotlinFunctionMetadata.flags), true);
            print("fun ");
            kotlinFunctionMetadata.typeParametersAccept(clazz, kotlinMetadata, context);
            kotlinFunctionMetadata.typeParametersAccept(
                clazz,
                kotlinMetadata,
                new KotlinTypeParameterVisitorWrapper(
                    (i, kotlinTypeParameterMetadata) -> print(i == 0 ? "<" : ", "),
                    MyKotlinSourceMetadataPrinter.this,
                    (i, kotlinTypeParameterMetadata) -> print(i == kotlinFunctionMetadata.typeParameters.size() - 1 ? "> " : "")));

            kotlinFunctionMetadata.receiverTypeAccept(clazz, kotlinMetadata, this);
            print(kotlinFunctionMetadata.name.replaceAll("^<(.*)>$", "$1"));
            print("(");
            kotlinFunctionMetadata.valueParametersAccept(clazz, kotlinMetadata, this);
            print(")");
            kotlinFunctionMetadata.returnTypeAccept(clazz, kotlinMetadata, this);
            println(" { }");
        }


        // Implementations for KotlinAnnotationVisitor.

        @Override
        public void visitAnyAnnotation(Clazz clazz, KotlinMetadataAnnotation annotation)
        {
            print("@");
            printKotlinAnnotation(annotation.kmAnnotation);
        }


        @Override
        public void visitAnyVersionRequirement(Clazz                            clazz,
                                               KotlinVersionRequirementMetadata kotlinVersionRequirementMetadata)
        {
            printVersionRequirement(kotlinVersionRequirementMetadata);
        }


        // Small helper methods.

        private void printVersionRequirement(KotlinVersionRequirementMetadata kotlinVersionRequirementMetadata)
        {
            printVersionRequirement("", kotlinVersionRequirementMetadata, false);
        }


        private void printVersionRequirement(String prefix, KotlinVersionRequirementMetadata kotlinVersionRequirementMetadata, boolean inline)
        {
            print(prefix + "@SinceKotlin(\"" +
                  kotlinVersionRequirementMetadata.major + "." +
                  kotlinVersionRequirementMetadata.minor + "." +
                  kotlinVersionRequirementMetadata.patch + "\")", !inline);
            if (!inline)
            {
                println();
            }
        }


        // Helper to visit the children of a clazz, based on the name.
        private void visitChildClasses(Clazz clazz)
        {
            // Cache the synthetic class string, as we might have already visited them, so the string will be empty.
            pushStringBuilder();
            programClassPool.classesAccept(
                    clazz.getName() + TypeConstants.INNER_CLASS_SEPARATOR + "*",
                    new ReferencedKotlinMetadataVisitor(MyKotlinSourceMetadataPrinter.this));
            String innerClassesString = popStringBuilder();
            if (innerClassesString.length() > 0)
            {
                println();
                println("// Synthetic inner classes - these were generated by the Kotlin compiler from e.g. lambdas", true);
                println();
                print(innerClassesString);
            }
        }
    }

    // Small utility methods.


    private void printHeader(Clazz clazz, KotlinMetadata kotlinMetadata)
    {
        if (context.isTop() && context.getPackageName().length() > 0)
        {
            println("package " + externalClassName(context.getPackageName()), true);
            println();
        }
        String metadataKindString = metadataKindToString(kotlinMetadata.k);
        if (kotlinMetadata.k == METADATA_KIND_CLASS && ((KotlinClassKindMetadata)kotlinMetadata).flags.isCompanionObject)
        {
            metadataKindString = "companion " + metadataKindString;
        }
        if (context.isTop())
        {
            println("/**", true);
            println("* Kotlin " + metadataKindString + ".", true);
            println("* From Java class: " + externalClassName(clazz.getName()), true);
            println("*/", true);
        }
        else
        {
            println("// Kotlin " + metadataKindString + " from Java class: " + externalClassName(clazz.getName()), true);
            if (kotlinMetadata.k == METADATA_KIND_CLASS && ((KotlinClassKindMetadata) kotlinMetadata).anonymousObjectOriginName != null)
            {
                println("// Anonymous object origin: " + externalClassName(((KotlinClassKindMetadata) kotlinMetadata).anonymousObjectOriginName), true);
            }
        }
    }


    private void printKotlinAnnotation(KmAnnotation kmAnnotation) {
        print(context.className(kmAnnotation.getClassName(), "."));
        print(kmAnnotation.getArguments().size() > 0 ? "(" : "");
        int i = 0;
        for (Map.Entry<String, KmAnnotationArgument<?>> entry : kmAnnotation.getArguments().entrySet()) {
            String string = entry.getKey();
            KmAnnotationArgument<?> kmAnnotationArgument = entry.getValue();
            if (i > 0) {
                print(", ");
            }
            print(string + " = ");
            printKmAnnotationArgument(kmAnnotationArgument);
            i++;
        }
        print(kmAnnotation.getArguments().size() > 0 ? ")" : "");
    }


    private void printKmAnnotationArgument(KmAnnotationArgument<?> kmAnnotationArgument) {

        Object value = kmAnnotationArgument.getValue();
        if (kmAnnotationArgument instanceof KmAnnotationArgument.StringValue)
        {
            print("\"" + kmAnnotationArgument.getValue() + "\"");
        }
        else if (kmAnnotationArgument instanceof KmAnnotationArgument.AnnotationValue)
        {
            printKotlinAnnotation((KmAnnotation) value);
        }
        else if (kmAnnotationArgument instanceof KmAnnotationArgument.KClassValue)
        {
            print(context.className(value.toString(), "."));
        }
        else if (kmAnnotationArgument instanceof KmAnnotationArgument.EnumValue)
        {
            KmAnnotationArgument.EnumValue enumValue = (KmAnnotationArgument.EnumValue) kmAnnotationArgument;
            print(context.className(enumValue.getEnumClassName() + "." + enumValue.getEnumEntryName(), "."));
        }
        else if (kmAnnotationArgument instanceof KmAnnotationArgument.ArrayValue)
        {
            KmAnnotationArgument.ArrayValue arrayValue = (KmAnnotationArgument.ArrayValue)kmAnnotationArgument;
            print("{");
            List<? extends KmAnnotationArgument<?>> values = arrayValue.getValue();
            for (int j = 0; j < values.size(); j++)
            {
                if (j > 0)
                {
                    print(", ");
                }
                printKmAnnotationArgument(values.get(j));
            }
            print("}");
        }
        else
        {
            print(value.toString());
        }
    }


    public void pushStringBuilder()
    {
        stringBuilders.push(new StringBuilder());
    }


    public String popStringBuilder()
    {
        StringBuilder sb = stringBuilders.pop();
        return sb.toString();
    }


    public void resetStringBuilder()
    {
        popStringBuilder();
        pushStringBuilder();
    }


    public char previousChar()
    {
        return stringBuilders.size() > 0 ? stringBuilders.peek().charAt(stringBuilders.peek().length() - 1) : (char)-1;
    }

    public void indent()
    {
        indentation += 1;
    }


    public void outdent()
    {
        indentation -= 1;
    }


    public void println(String string)
    {
        println(string, false);
    }


    public void println(String string, boolean indent)
    {
        print(string, indent);
        println();
    }


    public void print(String string)
    {
        print(string, false);
    }


    public void print(String string, boolean indent)
    {
        if (indent)
        {
            for (int index = 0; index < indentation; index++)
            {
                stringBuilders.peek().append(INDENTATION);
            }
        }

        stringBuilders.peek().append(string);
    }


    public void println()
    {
        stringBuilders.peek().append(lineSeparator());
    }


    // Flag printing helpers
    private String modalityFlags(boolean printAbstract, KotlinModalityFlags flags)
    {
        return
            (flags.isFinal         ? ""                      : "") +
            (flags.isOpen          ? "open "                 : "") +
            (flags.isAbstract && printAbstract ? "abstract " : "") +
            (flags.isSealed        ? "sealed "               : "");
    }


    private String visibilityFlags(KotlinVisibilityFlags flags)
    {
        return
            (flags.isInternal      ? ""           : "") +
            (flags.isPrivate       ? "private "   : "") +
            (flags.isPublic        ? ""           : "") + //default
            (flags.isProtected     ? "protected " : "") +
            (flags.isPrivateToThis ? ""           : "") +
            (flags.isLocal         ? ""           : "");
    }


    private String classFlags(KotlinClassFlags flags)
    {
       return
           visibilityFlags(flags.visibility) + modalityFlags(!flags.isInterface, flags.modality) +
           (flags.isAnnotationClass ? "annotation class "  : "") +
           (flags.isInner           ? "inner "             : "") + // Also isUsualClass = true.
           (flags.isData            ? "data "              : "") + // Also isUsualClass = true.
           (flags.isInline          ? "inline "            : "") + // Also isUsualClass = true.
           (flags.isUsualClass      ? "class "             : "") +
           (flags.isInterface       ? "interface "         : "") +
           (flags.isObject          ? "object "            : "") +
           (flags.isExpect          ? "expect "            : "") +
           (flags.isExternal        ? "external "          : "") +
           (flags.isCompanionObject ? "companion object "  : "") +
           (flags.isEnumEntry       ? "enum entry "        : "") +
           (flags.isEnumClass       ? "enum class "        : "");
    }


    private String effectExpressionFlags(KotlinEffectExpressionFlags flags)
    {
        return
            (flags.isNegated ? "negated " : "") +
            (flags.isNullCheckPredicate ? "nullCheckPredicate " : "");
    }


    private String functionFlags(KotlinFunctionFlags flags)
    {
        return
            visibilityFlags(flags.visibility) + modalityFlags(true, flags.modality) +
            (flags.isDeclaration  ? ""                 : "") +
            (flags.isFakeOverride ? "fakeOverride "    : "") +
            (flags.isDelegation   ? "by "              : "") +
            (flags.isSynthesized  ? "/* synthetic */ " : "") +
            (flags.isInline       ? "inline "          : "") +
            (flags.isInfix        ? "infix "           : "") +
            (flags.isOperator     ? "operator "        : "") +
            (flags.isTailrec      ? "tailrec "         : "") +
            (flags.isExternal     ? "external "        : "") +
            (flags.isSuspend      ? "suspend "         : "") +
            (flags.isExpect       ? "expect "          : "");
    }


    private String propertyAccessorFlags(KotlinPropertyAccessorFlags flags)
    {
        return
            visibilityFlags(flags.visibility) + modalityFlags(true, flags.modality) +
            (flags.isDefault  ? ""          : "") +
            (flags.isExternal ? "external " : "") +
            (flags.isInline   ? "inline "   : "");
    }


    private String propertyFlags(KotlinPropertyFlags flags)
    {
        return
            visibilityFlags(flags.visibility) + modalityFlags(true, flags.modality) +
            (flags.isDeclared     ? ""              : "") +
            (flags.isFakeOverride ? "fakeOverride " : "") +
            (flags.isDelegation   ? "by "           : "") +
            (flags.isSynthesized  ? "/* synthetic */ "    : "") +
            (flags.isVar          ? "var "          : "val ") +
            (flags.isConst        ? "const "        : "") +
            (flags.isLateinit     ? "lateinit "     : "") +
            (flags.hasConstant    ? ""  : "") +
            (flags.isExternal     ? "external "     : "") +
            (flags.isDelegated    ? "/* delegated */ "    : "") +
            (flags.isExpect       ? "expect "       : "") +
            //JVM specific flags
            (flags.isMovedFromInterfaceCompanion ? "movedFromInterfaceCompanion " : "");
    }


    private String typeFlags(KotlinTypeFlags flags)
    {
        return
            //(flags.isNullable ? "nullable " : "") + //printed as ? after name in printKotlinTypeMetadata
            (flags.isSuspend  ? "suspend " : "");
    }


    private String typeParameterFlags(KotlinTypeParameterFlags flags)
    {
        return flags.isReified ? "reified " : "";
    }


    private String valueParameterFlags(KotlinValueParameterFlags flags)
    {
        return
            (flags.isCrossInline   ? "crossinline " : "") +
            (flags.isNoInline      ? "noinline "    : "") +
            (flags.hasDefaultValue ? ""  : "");
    }
}
