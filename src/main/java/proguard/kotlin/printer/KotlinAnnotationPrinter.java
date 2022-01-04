/*
 * ProGuard -- shrinking, optimization, obfuscation, and preverification
 *             of Java bytecode.
 *
 * Copyright (c) 2002-2021 Guardsquare NV
 */

package proguard.kotlin.printer;

import proguard.classfile.Clazz;
import proguard.classfile.kotlin.*;
import proguard.classfile.kotlin.visitor.KotlinAnnotationArgumentVisitor;
import proguard.classfile.kotlin.visitor.KotlinAnnotationVisitor;

import java.util.Collections;

import static proguard.classfile.kotlin.KotlinAnnotationArgument.*;

/**
 * Helper class to print Kotlin annotations that are attached to Kotlin metadata elements,
 * as opposed to those attached to Java elements.
 *
 * @author James Hamilton
 */
public class KotlinAnnotationPrinter
implements   KotlinAnnotationVisitor,
             KotlinAnnotationArgumentVisitor
{
    private final KotlinSourcePrinter printer;
    private boolean                   inline;
    private       int                 level = 0;


    public KotlinAnnotationPrinter(KotlinSourcePrinter printer)
    {
        this.printer = printer;
    }


    @Override
    public void visitAnyAnnotation(Clazz             clazz,
                                   KotlinAnnotatable kotlinAnnotatable,
                                   KotlinAnnotation  kotlinAnnotation)
    {
        if (level == 0) printer.print("@", !this.inline);
        level++;
        printer.print(printer.getContext().className(kotlinAnnotation.className, "."));
        if (!kotlinAnnotation.arguments.isEmpty())
        {
            printer.print("(");
        }
        kotlinAnnotation.argumentsAccept(clazz, kotlinAnnotatable, this);
        if (!kotlinAnnotation.arguments.isEmpty())
        {
            printer.print(")");
        }
        level--;
        if (level == 0) printer.print(this.inline ? " " : System.lineSeparator());
    }


    @Override
    public void visitTypeAnnotation(Clazz              clazz,
                                    KotlinTypeMetadata kotlinTypeMetadata,
                                    KotlinAnnotation   annotation)
    {
        this.inline = true;
        visitAnyAnnotation(clazz, kotlinTypeMetadata, annotation);
    }


    @Override
    public void visitTypeParameterAnnotation(Clazz                       clazz,
                                             KotlinTypeParameterMetadata kotlinTypeParameterMetadata,
                                             KotlinAnnotation            annotation)
    {
        this.inline = true;
        visitAnyAnnotation(clazz, kotlinTypeParameterMetadata, annotation);
    }


    @Override
    public void visitTypeAliasAnnotation(Clazz                   clazz,
                                         KotlinTypeAliasMetadata kotlinTypeAliasMetadata,
                                         KotlinAnnotation        annotation)
    {
        this.inline = false;
        visitAnyAnnotation(clazz, kotlinTypeAliasMetadata, annotation);
    }


    @Override
    public void visitAnyArgument(Clazz                    clazz,
                                 KotlinAnnotatable        annotatable,
                                 KotlinAnnotation         annotation,
                                 KotlinAnnotationArgument argument,
                                 Value                    value)
    {
        char previousChar = printer.previousChar();
        if (previousChar != '(' && previousChar != '[')
        {
            printer.print(", ");
        }
        printer.print(argument.name + " = ");
    }


    @Override
    public void visitAnyLiteralArgument(Clazz                                    clazz,
                                        KotlinAnnotatable                        annotatable,
                                        KotlinAnnotation                         annotation,
                                        KotlinAnnotationArgument                 argument,
                                        KotlinAnnotationArgument.LiteralValue<?> value)
    {
        visitAnyArgument(clazz, annotatable, annotation, argument, value);
        printer.print(value.toString());
    }


    @Override
    public void visitUByteArgument(Clazz clazz, KotlinAnnotatable annotatable, KotlinAnnotation annotation, KotlinAnnotationArgument argument, UByteValue value)
    {
        visitAnyArgument(clazz, annotatable, annotation, argument, value);
        printer.print(String.valueOf(Byte.toUnsignedInt(value.value)));
    }


    @Override
    public void visitUShortArgument(Clazz clazz, KotlinAnnotatable annotatable, KotlinAnnotation annotation, KotlinAnnotationArgument argument, UShortValue value)
    {
        visitAnyArgument(clazz, annotatable, annotation, argument, value);
        printer.print(String.valueOf(Short.toUnsignedInt(value.value)));
    }


    @Override
    public void visitUIntArgument(Clazz clazz, KotlinAnnotatable annotatable, KotlinAnnotation annotation, KotlinAnnotationArgument argument, UIntValue value)
    {
        visitAnyArgument(clazz, annotatable, annotation, argument, value);
        printer.print(Integer.toUnsignedString(value.value));
    }


    @Override
    public void visitULongArgument(Clazz clazz, KotlinAnnotatable annotatable, KotlinAnnotation annotation, KotlinAnnotationArgument argument, ULongValue value)
    {
        visitAnyArgument(clazz, annotatable, annotation, argument, value);
        printer.print(Long.toUnsignedString(value.value));
    }


    @Override
    public void visitArrayArgument(Clazz                    clazz,
                                   KotlinAnnotatable        annotatable,
                                   KotlinAnnotation         annotation,
                                   KotlinAnnotationArgument argument,
                                   ArrayValue               value)
    {
        visitAnyArgument(clazz, annotatable, annotation, argument, value);
        printer.print("[");
        value.elementsAccept(clazz, annotatable, annotation, argument, this);
        printer.print("]");
    }


    @Override
    public void visitStringArgument(Clazz                    clazz,
                                    KotlinAnnotatable        annotatable,
                                    KotlinAnnotation         annotation,
                                    KotlinAnnotationArgument argument,
                                    StringValue              value)
    {
        visitAnyArgument(clazz, annotatable, annotation, argument, value);
        printer.print("\"" + value.toString() + "\"");
    }


    @Override
    public void visitClassArgument(Clazz                               clazz,
                                   KotlinAnnotatable                   annotatable,
                                   KotlinAnnotation                    annotation,
                                   KotlinAnnotationArgument            argument,
                                   KotlinAnnotationArgument.ClassValue value)
    {
        visitAnyArgument(clazz, annotatable, annotation, argument, value);
        String className = printer.getContext().className(value.className, ".");
        printer.print(
            String.join("", Collections.nCopies(value.arrayDimensionsCount, "Array<")) +
            className +
            String.join("", Collections.nCopies(value.arrayDimensionsCount, ">")) +
            "::class"
        );
    }


    @Override
    public void visitAnnotationArgument(Clazz                    clazz,
                                        KotlinAnnotatable        annotatable,
                                        KotlinAnnotation         annotation,
                                        KotlinAnnotationArgument argument,
                                        AnnotationValue          value)
    {
        visitAnyArgument(clazz, annotatable, annotation, argument, value);
        value.annotationAccept(clazz, annotatable, this);
    }


    @Override
    public void visitEnumArgument(Clazz                    clazz,
                                  KotlinAnnotatable        annotatable,
                                  KotlinAnnotation         annotation,
                                  KotlinAnnotationArgument argument,
                                  EnumValue                value)
    {
        visitAnyArgument(clazz, annotatable, annotation, argument, value);
        printer.print(printer.getContext().className(value.className, ".") + "." + value.enumEntryName);
    }
}
