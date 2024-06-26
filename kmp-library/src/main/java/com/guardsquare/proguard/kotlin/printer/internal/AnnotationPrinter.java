/*
 * ProGuard -- shrinking, optimization, obfuscation, and preverification
 *             of Java bytecode.
 *
 * Copyright (c) 2002-2020 Guardsquare NV
 */

package com.guardsquare.proguard.kotlin.printer.internal;

import com.guardsquare.proguard.kotlin.printer.KotlinMetadataPrinter;
import com.guardsquare.proguard.kotlin.printer.internal.visitor.ConstantToStringVisitor;
import java.util.HashMap;
import java.util.Map;
import proguard.classfile.Clazz;
import proguard.classfile.TypeConstants;
import proguard.classfile.attribute.Attribute;
import proguard.classfile.attribute.annotation.Annotation;
import proguard.classfile.attribute.annotation.AnnotationElementValue;
import proguard.classfile.attribute.annotation.AnnotationsAttribute;
import proguard.classfile.attribute.annotation.ArrayElementValue;
import proguard.classfile.attribute.annotation.ClassElementValue;
import proguard.classfile.attribute.annotation.ConstantElementValue;
import proguard.classfile.attribute.annotation.ElementValue;
import proguard.classfile.attribute.annotation.EnumConstantElementValue;
import proguard.classfile.attribute.annotation.visitor.AnnotationTypeFilter;
import proguard.classfile.attribute.annotation.visitor.AnnotationVisitor;
import proguard.classfile.attribute.annotation.visitor.ElementValueVisitor;
import proguard.classfile.attribute.visitor.AttributeVisitor;
import proguard.classfile.kotlin.KotlinConstants;
import proguard.classfile.util.ClassUtil;

/**
 * Helper class to print annotations that are attached to Java elements,
 * as opposed to those attached to Kotlin metadata.
 *
 * @author James Hamilton
 */
public class AnnotationPrinter
implements   AttributeVisitor,
             AnnotationVisitor,
             ElementValueVisitor
{
    private final KotlinMetadataPrinter printer;
    private final boolean                     inline;
    private       int                         level = 0;
    private static final Map<Character, String> primitiveTypeToClass = new HashMap<>();

    static {
        primitiveTypeToClass.put(TypeConstants.BOOLEAN, "Boolean::class");
        primitiveTypeToClass.put(TypeConstants.BYTE, "Byte::class");
        primitiveTypeToClass.put(TypeConstants.CHAR, "Char::class");
        primitiveTypeToClass.put(TypeConstants.SHORT, "Short::class");
        primitiveTypeToClass.put(TypeConstants.INT, "Int::class");
        primitiveTypeToClass.put(TypeConstants.FLOAT, "Float::class");
        primitiveTypeToClass.put(TypeConstants.LONG, "Long::class");
        primitiveTypeToClass.put(TypeConstants.DOUBLE, "Double::class");
    }

    public AnnotationPrinter(KotlinMetadataPrinter printer)
    {
        this(printer, false);
    }

    public AnnotationPrinter(KotlinMetadataPrinter printer, boolean inline)
    {
        this.printer = printer;
        this.inline  = inline;
    }


    @Override
    public void visitAnyAttribute(Clazz clazz, Attribute attribute) { }


    @Override
    public void visitAnyAnnotationsAttribute(Clazz clazz, AnnotationsAttribute annotationsAttribute)
    {
        annotationsAttribute.annotationsAccept(clazz,
                                               new AnnotationTypeFilter("!" + KotlinConstants.TYPE_KOTLIN_METADATA +
                                                                            ",!Lorg/jetbrains/annotations/NotNull;" +
                                                                            ",!Lorg/jetbrains/annotations/Nullable;",
                                                                        null,
                                                                        this));
    }


    @Override
    public void visitAnnotation(Clazz clazz, Annotation annotation)
    {
        if (level == 0) printer.print("@", !this.inline);
        level++;
        printer.print(printer.getContext().className(ClassUtil.internalClassNameFromType(annotation.getType(clazz)), "."));
        if (annotation.u2elementValuesCount > 0)
        {
            printer.print("(");
        }
        annotation.elementValuesAccept(clazz, this);
        if (annotation.u2elementValuesCount > 0)
        {
            printer.print(")");
        }
        level--;
        if (level == 0) printer.print(this.inline ? "" : System.lineSeparator());
    }


    @Override
    public void visitAnyElementValue(Clazz clazz, Annotation annotation, ElementValue elementValue)
    {
        char previousChar = printer.previousChar();
        if (previousChar != '(' && previousChar != '{')
        {
            printer.print(", ");
        }
        try
        {
            String methodName = elementValue.getMethodName(clazz);
            if (!methodName.equals("value"))
            {
                printer.print(methodName + " = ");
            }
        }
        catch (NullPointerException ignored) {}
    }


    @Override
    public void visitConstantElementValue(Clazz                clazz,
                                          Annotation           annotation,
                                          ConstantElementValue constantElementValue)
    {
        visitAnyElementValue(clazz, annotation, constantElementValue);
        clazz.constantPoolEntryAccept(constantElementValue.u2constantValueIndex, new ConstantToStringVisitor(printer::print));
    }


    @Override
    public void visitEnumConstantElementValue(Clazz                    clazz,
                                              Annotation               annotation,
                                              EnumConstantElementValue enumConstantElementValue)
    {
        visitAnyElementValue(clazz, annotation, enumConstantElementValue);
        printer.print(printer.getContext().className(ClassUtil.internalClassNameFromType(enumConstantElementValue.getTypeName(clazz)), "."));
        printer.print("." + enumConstantElementValue.getConstantName(clazz));
    }

    @Override
    public void visitClassElementValue(Clazz clazz, Annotation annotation, ClassElementValue classElementValue)
    {
        visitAnyElementValue(clazz, annotation, classElementValue);
        String internalClassName;
        String className = classElementValue.getClassName(clazz);
        if (ClassUtil.isInternalPrimitiveType(className)) {
            internalClassName = primitiveTypeToClass.get(className.charAt(0));
        } else {
            internalClassName = ClassUtil.internalClassNameFromType(className);
        }
        printer.print(printer.getContext().className(internalClassName, "."));
    }

    @Override
    public void visitAnnotationElementValue(Clazz                  clazz,
                                            Annotation             annotation,
                                            AnnotationElementValue annotationElementValue)
    {

        visitAnyElementValue(clazz, annotation, annotationElementValue);
        annotationElementValue.annotationAccept(clazz, this);
    }


    @Override
    public void visitArrayElementValue(Clazz clazz, Annotation annotation, ArrayElementValue arrayElementValue)
    {
        visitAnyElementValue(clazz, annotation, arrayElementValue);
        printer.print("{");
        arrayElementValue.elementValuesAccept(clazz, annotation, this);
        printer.print("}");
    }
}
