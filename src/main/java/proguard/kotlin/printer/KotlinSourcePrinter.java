/*
 * ProGuard -- shrinking, optimization, obfuscation, and preverification
 *             of Java bytecode.
 *
 * Copyright (c) 2002-2022 Guardsquare NV
 */
package proguard.kotlin.printer;

import proguard.classfile.ClassPool;
import proguard.classfile.Clazz;
import proguard.classfile.Method;
import proguard.classfile.TypeConstants;
import proguard.classfile.attribute.annotation.Annotation;
import proguard.classfile.attribute.annotation.visitor.AllAnnotationVisitor;
import proguard.classfile.attribute.annotation.visitor.AnnotationTypeFilter;
import proguard.classfile.attribute.annotation.visitor.AnnotationVisitor;
import proguard.classfile.attribute.visitor.AllAttributeVisitor;
import proguard.classfile.attribute.visitor.AttributeConstantVisitor;
import proguard.classfile.kotlin.KotlinClassKindMetadata;
import proguard.classfile.kotlin.KotlinConstants;
import proguard.classfile.kotlin.KotlinConstructorMetadata;
import proguard.classfile.kotlin.KotlinDeclarationContainerMetadata;
import proguard.classfile.kotlin.KotlinFileFacadeKindMetadata;
import proguard.classfile.kotlin.KotlinFunctionMetadata;
import proguard.classfile.kotlin.KotlinMetadata;
import proguard.classfile.kotlin.KotlinMultiFileFacadeKindMetadata;
import proguard.classfile.kotlin.KotlinMultiFilePartKindMetadata;
import proguard.classfile.kotlin.KotlinPropertyMetadata;
import proguard.classfile.kotlin.KotlinSyntheticClassKindMetadata;
import proguard.classfile.kotlin.KotlinTypeAliasMetadata;
import proguard.classfile.kotlin.KotlinTypeMetadata;
import proguard.classfile.kotlin.KotlinTypeParameterMetadata;
import proguard.classfile.kotlin.KotlinValueParameterMetadata;
import proguard.classfile.kotlin.KotlinVersionRequirementMetadata;
import proguard.classfile.kotlin.flags.KotlinClassFlags;
import proguard.classfile.kotlin.flags.KotlinEffectExpressionFlags;
import proguard.classfile.kotlin.flags.KotlinFunctionFlags;
import proguard.classfile.kotlin.flags.KotlinModalityFlags;
import proguard.classfile.kotlin.flags.KotlinPropertyAccessorFlags;
import proguard.classfile.kotlin.flags.KotlinPropertyFlags;
import proguard.classfile.kotlin.flags.KotlinTypeFlags;
import proguard.classfile.kotlin.flags.KotlinTypeParameterFlags;
import proguard.classfile.kotlin.flags.KotlinValueParameterFlags;
import proguard.classfile.kotlin.flags.KotlinVisibilityFlags;
import proguard.classfile.kotlin.visitor.AllKotlinAnnotationArgumentVisitor;
import proguard.classfile.kotlin.visitor.AllTypeParameterVisitor;
import proguard.classfile.kotlin.visitor.KotlinClassToAnonymousObjectOriginClassVisitor;
import proguard.classfile.kotlin.visitor.KotlinClassToInlineOriginFunctionVisitor;
import proguard.classfile.kotlin.visitor.KotlinConstructorVisitor;
import proguard.classfile.kotlin.visitor.KotlinFunctionVisitor;
import proguard.classfile.kotlin.visitor.KotlinMetadataVisitor;
import proguard.classfile.kotlin.visitor.KotlinPropertyVisitor;
import proguard.classfile.kotlin.visitor.KotlinTypeAliasVisitor;
import proguard.classfile.kotlin.visitor.KotlinTypeParameterVisitor;
import proguard.classfile.kotlin.visitor.KotlinTypeVisitor;
import proguard.classfile.kotlin.visitor.KotlinValueParameterVisitor;
import proguard.classfile.kotlin.visitor.KotlinVersionRequirementVisitor;
import proguard.classfile.kotlin.visitor.MultiKotlinMetadataVisitor;
import proguard.classfile.kotlin.visitor.ReferencedKotlinMetadataVisitor;
import proguard.classfile.kotlin.visitor.filter.KotlinAnnotationArgumentFilter;
import proguard.classfile.kotlin.visitor.filter.KotlinAnnotationFilter;
import proguard.classfile.kotlin.visitor.filter.KotlinConstructorFilter;
import proguard.classfile.kotlin.visitor.filter.KotlinMetadataFilter;
import proguard.classfile.kotlin.visitor.filter.KotlinTypeFilter;
import proguard.classfile.util.ClassUtil;
import proguard.classfile.visitor.ClassCounter;
import proguard.classfile.visitor.MultiClassVisitor;
import proguard.classfile.visitor.MultiMemberVisitor;
import proguard.kotlin.printer.visitor.ConstantToStringVisitor;
import proguard.kotlin.printer.visitor.KotlinClassTypeParameterVisitor;
import proguard.kotlin.printer.visitor.KotlinMetadataVisitorWrapper;
import proguard.kotlin.printer.visitor.KotlinTypeParameterVisitorWrapper;
import proguard.kotlin.printer.visitor.KotlinTypeVisitorWrapper;

import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.System.lineSeparator;
import static java.util.Objects.requireNonNull;
import static proguard.classfile.kotlin.KotlinConstants.METADATA_KIND_CLASS;
import static proguard.classfile.kotlin.KotlinConstants.METADATA_KIND_MULTI_FILE_CLASS_PART;
import static proguard.classfile.kotlin.KotlinConstants.METADATA_KIND_SYNTHETIC_CLASS;
import static proguard.classfile.kotlin.KotlinConstants.NAME_KOTLIN_ANY;
import static proguard.classfile.kotlin.KotlinConstants.NAME_KOTLIN_ENUM;
import static proguard.classfile.kotlin.KotlinConstants.NAME_KOTLIN_EXTENSION_FUNCTION;
import static proguard.classfile.kotlin.KotlinConstants.NAME_KOTLIN_FUNCTION;
import static proguard.classfile.kotlin.KotlinConstants.NAME_KOTLIN_PARAMETER_NAME;
import static proguard.classfile.kotlin.KotlinConstants.NAME_KOTLIN_UNIT;
import static proguard.classfile.kotlin.KotlinConstants.metadataKindToString;
import static proguard.classfile.kotlin.KotlinTypeVariance.INVARIANT;
import static proguard.classfile.util.ClassUtil.externalClassName;
import static proguard.classfile.util.ClassUtil.externalShortClassName;

/**
 * Prints the Kotlin metadata annotation in a format similar to
 * Kotlin source code.
 * <p>
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
    private       int                           indentation;
    private       Context                       context;
    private final boolean excludeEmbedded;


    public KotlinSourcePrinter(ClassPool programClassPool)
    {
        this(programClassPool, true);
    }

    public KotlinSourcePrinter(ClassPool programClassPool, boolean excludeEmbedded)
    {
        this.programClassPool = programClassPool;
        this.excludeEmbedded = excludeEmbedded;
    }


    @Override
    public void visitAnyKotlinMetadata(Clazz clazz, KotlinMetadata kotlinMetadata)
    {
        if (context == null)
        {
            this.context = new Context();
        }
        pushStringBuilder();
        KotlinMetadataVisitor printer = KotlinSourcePrinter.this.printer;

        if (excludeEmbedded) {
            // Inner printer gets executed, then the string is written to the visitorInfo.
            // We only print non-synthetic classes directly; synthetic classes will be
            // printed within non-synthetic ones. Likewise, multi-file class parts will
            // be printed in their facades.
            printer = new KotlinMetadataFilter(
                    _kotlinMetadata -> _kotlinMetadata.k != METADATA_KIND_SYNTHETIC_CLASS &&
                                       _kotlinMetadata.k != METADATA_KIND_MULTI_FILE_CLASS_PART,
                    printer
            );
        }

        kotlinMetadata.accept(clazz, printer);

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
            context.push(new ContextFrame(clazz, kotlinClassKindMetadata));

            printHeader(clazz, kotlinClassKindMetadata);

            clazz.attributesAccept(new AnnotationPrinter(KotlinSourcePrinter.this));

            kotlinClassKindMetadata.versionRequirementAccept(clazz,
                (_clazz, versionRequirement) -> printVersionRequirement(versionRequirement));

            kotlinClassKindMetadata.contextReceiverTypesAccept(clazz, new KotlinTypeVisitorWrapper(
                (i, _kotlinTypeMetadata) -> print(i == 0 ? "context(" : ", ", true),
                this,
                (i, _kotlinTypeMetadata) -> {
                    if (i == kotlinClassKindMetadata.contextReceivers.size() - 1) println(")");
                }));

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
                    new KotlinClassTypeParameterVisitor(context),
                    // Including the type parameters declared in the anonymous object origin location hierarchy.
                    new KotlinClassToAnonymousObjectOriginClassVisitor(programClassPool,
                        new MultiKotlinMetadataVisitor(
                        new KotlinClassToInlineOriginFunctionVisitor(kotlinClassKindMetadata.anonymousObjectOriginName,
                            new AllTypeParameterVisitor(context)),
                        new AllTypeParameterVisitor(context))),
                    // Then print them.
                    new KotlinClassTypeParameterVisitor(
                    new KotlinTypeParameterVisitorWrapper(
                        (i, kotlinTypeParameterMetadata) -> print(i == 0 ? "<" : ", "),
                        MyKotlinSourceMetadataPrinter.this,
                        (i, kotlinTypeParameterMetadata) -> print(i == kotlinClassKindMetadata.typeParameters.size() - 1 ? ">" : "")))));

            pushStringBuilder();
            kotlinClassKindMetadata.constructorsAccept(clazz,
                new KotlinConstructorFilter(
                    constructor -> !constructor.flags.isSecondary && !constructor.isParameterless(),
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
                    new KotlinConstructorFilter(constructor -> constructor.flags.isSecondary,
                        MyKotlinSourceMetadataPrinter.this));
            }

            if (kotlinClassKindMetadata.flags.isValue)
            {
                println("// Underlying property name: " + kotlinClassKindMetadata.underlyingPropertyName, true);
                pushStringBuilder();
                kotlinClassKindMetadata.inlineClassUnderlyingPropertyTypeAccept(clazz, MyKotlinSourceMetadataPrinter.this);
                String underlyingPropertyType = popStringBuilder();
                println("// Underlying property type: " + underlyingPropertyType, true);
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
            int doubleUnderscoreIndex = shortClassName.indexOf("__");
            if (doubleUnderscoreIndex != -1)
            {
                println("@file:JvmName(\"" + shortClassName.substring(0, doubleUnderscoreIndex).replaceFirst("Kt$", "") + "\")", true);
            }
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
                (_clazz, versionRequirement) -> printVersionRequirement(!kotlinConstructorMetadata.flags.isSecondary ? " " : "",
                                                                        versionRequirement,
                                                                        !kotlinConstructorMetadata.flags.isSecondary));
            AtomicInteger annotationsCount = new AtomicInteger(0);

            if (kotlinConstructorMetadata.flags.common.hasAnnotations &&
                kotlinConstructorMetadata.jvmSignature != null)
            {
                pushStringBuilder();
                Method method = clazz.findMethod(kotlinConstructorMetadata.jvmSignature.method,
                                                 kotlinConstructorMetadata.jvmSignature.descriptor.toString());
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
                                                        !kotlinConstructorMetadata.flags.isSecondary))));
                }
                String annotationsString = popStringBuilder();

                if (annotationsCount.get() > 0)
                {
                    print((!kotlinConstructorMetadata.flags.isSecondary ? " " : "") + annotationsString);
                }
            }

            if (kotlinConstructorMetadata.flags.isSecondary           ||
                 kotlinConstructorMetadata.versionRequirement != null ||
                 annotationsCount.get()                   > 0)
            {
                print(!kotlinConstructorMetadata.flags.isSecondary ? " " : "", kotlinConstructorMetadata.flags.isSecondary);
                print("constructor");
            }
            print("(");
            kotlinConstructorMetadata.valueParametersAccept(clazz, kotlinClassKindMetadata, this);
            print(") ");
            if (kotlinConstructorMetadata.flags.isSecondary)
            {
                println("{ }");
            }
        }


        // Implementations for KotlinTypeParameterVisitor

        @Override
        public void visitAnyTypeParameter(Clazz clazz, KotlinTypeParameterMetadata kotlinTypeParameterMetadata)
        {
            // Only print in or out (invariant is default).
            if (!INVARIANT.equals(kotlinTypeParameterMetadata.variance))
            {
                print(kotlinTypeParameterMetadata.variance.toString().toLowerCase());
                print(" ");
            }

            print(typeParameterFlags(kotlinTypeParameterMetadata.flags));

            kotlinTypeParameterMetadata.annotationsAccept(clazz, new KotlinAnnotationPrinter(KotlinSourcePrinter.this));

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

            kotlinPropertyMetadata.contextReceiverTypesAccept(clazz, kotlinDeclarationContainerMetadata, new KotlinTypeVisitorWrapper(
                (i, _kotlinTypeMetadata) -> print(i == 0 ? "context(" : ", ", true),
                this,
                (i, _kotlinTypeMetadata) -> {
                    if (i == kotlinPropertyMetadata.contextReceivers.size() - 1) println(")");
                }));

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
                clazz.fieldAccept(kotlinPropertyMetadata.backingFieldSignature.memberName, kotlinPropertyMetadata.backingFieldSignature.descriptor.toString(),
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
                            kotlinPropertyMetadata.backingFieldSignature.memberName,
                            kotlinPropertyMetadata.backingFieldSignature.descriptor.toString()));
                }
                catch (IllegalArgumentException e)
                {
                    print("invalid field descriptor: " + kotlinPropertyMetadata.backingFieldSignature);
                }
                outdent();
            }


            if (kotlinPropertyMetadata.syntheticMethodForDelegate != null)
            {
                indent();
                println();
                print("// Synthetic method for delegate: ", true);
                print(ClassUtil.externalFullMethodDescription(
                        clazz.getName(),
                        kotlinPropertyMetadata.referencedSyntheticMethodForDelegateMethod.getAccessFlags(),
                        kotlinPropertyMetadata.referencedSyntheticMethodForDelegateMethod.getName(clazz),
                        kotlinPropertyMetadata.referencedSyntheticMethodForDelegateMethod.getDescriptor(clazz)));
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
                new KotlinAnnotationFilter(annotation -> annotation.className.equals(NAME_KOTLIN_EXTENSION_FUNCTION),
                    (clazz1, annotatable, annotation) -> isExtensionFunctionType.set(true),
                // A function type can optionally include names for the function parameters (for documentation purposes).
                new KotlinAnnotationFilter(annotation -> annotation.className.equals(NAME_KOTLIN_PARAMETER_NAME),
                    new AllKotlinAnnotationArgumentVisitor(
                    new KotlinAnnotationArgumentFilter(argument -> argument.name.equals("name"),
                        ((clazz1, annotatable, annotation, argument, value) -> parameterName.set(value.toString()))
                    )),
                // Else print the annotation.
                new KotlinAnnotationPrinter(KotlinSourcePrinter.this))));

            print(typeFlags(kotlinTypeMetadata.flags));

            if (parameterName.get().length() > 0)
            {
                print(parameterName + ": ");
            }

            if (kotlinTypeMetadata.variance != null && !kotlinTypeMetadata.variance.equals(INVARIANT))
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
            kotlinTypeAliasMetadata.annotationsAccept(clazz, new KotlinAnnotationPrinter(KotlinSourcePrinter.this));

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
            kotlinFunctionMetadata.referencedMethodAccept(new AllAttributeVisitor(new AnnotationPrinter(KotlinSourcePrinter.this)));
            kotlinFunctionMetadata.versionRequirementAccept(clazz, kotlinMetadata, this);
            kotlinFunctionMetadata.contextReceiverTypesAccept(clazz, kotlinMetadata, new KotlinTypeVisitorWrapper(
                (i, _kotlinTypeMetadata) -> print(i == 0 ? "context(" : ", ", true),
                this,
                (i, _kotlinTypeMetadata) -> {
                    if (i == kotlinFunctionMetadata.contextReceivers.size() - 1) println(")");
                }));
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
            print("* Kotlin " + metadataKindString + " ", false);
            println("(metadata version " + kotlinMetadata.mv[0] + "." + kotlinMetadata.mv[1] + "." + kotlinMetadata.mv[2] + ").", true);
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
            (flags.isInternal      ? "internal "  : "") +
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
           (flags.isValue           ? "value "             : (flags.isInline ? "inline " : "")) + // Also isUsualClass = true.
           (flags.isUsualClass      ? "class "             : "") +
           (flags.isFun             ? "fun "               : "") +
           (flags.isInterface       ? "interface "         : "") +
           (flags.isObject          ? "object "            : "") +
           (flags.isExpect          ? "expect "            : "") +
           (flags.isExternal        ? "external "          : "") +
           (flags.isCompanionObject ? "companion object "  : "") +
           (flags.isEnumEntry       ? "enum entry "        : "") +
           (flags.isEnumClass       ? "enum class "        : "") +
           // JVM specific flags
           (flags.isCompiledInCompatibilityMode ? "/* compiledInCompatibilityMode */ " : "") +
           (flags.hasMethodBodiesInInterface    ? "/* hasMethodBodiesInInterface */"  : "");
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
