/*
 * ProGuard -- shrinking, optimization, obfuscation, and preverification
 *             of Java bytecode.
 *
 * Copyright (c) 2002-2022 Guardsquare NV
 */

package com.guardsquare.proguard.kotlin.printer.internal.visitor;

import proguard.classfile.Clazz;
import proguard.classfile.kotlin.KotlinClassKindMetadata;
import proguard.classfile.kotlin.KotlinConstructorMetadata;
import proguard.classfile.kotlin.KotlinDeclarationContainerMetadata;
import proguard.classfile.kotlin.KotlinEffectExpressionMetadata;
import proguard.classfile.kotlin.KotlinFunctionMetadata;
import proguard.classfile.kotlin.KotlinMetadata;
import proguard.classfile.kotlin.KotlinPropertyMetadata;
import proguard.classfile.kotlin.KotlinTypeAliasMetadata;
import proguard.classfile.kotlin.KotlinTypeMetadata;
import proguard.classfile.kotlin.KotlinTypeParameterMetadata;
import proguard.classfile.kotlin.KotlinValueParameterMetadata;
import proguard.classfile.kotlin.visitor.KotlinTypeVisitor;

import java.util.function.BiConsumer;

/**
 * This KotlinTypeVisitor wraps another type visitor and executes the
 * provided functions before and after.
 *
 * @author James Hamilton
 */
public class KotlinTypeVisitorWrapper
implements KotlinTypeVisitor
{
    private final KotlinTypeVisitor                       kotlinTypeVisitor;
    private final BiConsumer<Integer, KotlinTypeMetadata> before;
    private final BiConsumer<Integer, KotlinTypeMetadata> after;
    private       int                                     i = 0;


    public KotlinTypeVisitorWrapper(BiConsumer<Integer, KotlinTypeMetadata> before,
                                    KotlinTypeVisitor kotlinTypeVisitor,
                                    BiConsumer<Integer, KotlinTypeMetadata> after)
    {
        this.kotlinTypeVisitor = kotlinTypeVisitor;
        this.before            = before;
        this.after             = after;
    }


    public KotlinTypeVisitorWrapper(BiConsumer<Integer, KotlinTypeMetadata> before, KotlinTypeVisitor kotlinTypeVisitor)
    {
        this(before, kotlinTypeVisitor, null);
    }


    private void before(KotlinTypeMetadata kotlinTypeMetadata)
    {
        this.before.accept(i, kotlinTypeMetadata);
    }


    private void after(KotlinTypeMetadata kotlinTypeMetadata)
    {
        if (this.after != null)
        {
            this.after.accept(i, kotlinTypeMetadata);
        }
        i++;
    }

    // Implements for KotlinTypeVisitor.

    @Override
    public void visitAnyType(Clazz clazz, KotlinTypeMetadata kotlinTypeMetadata) {}


    @Override
    public void visitTypeUpperBound(Clazz clazz, KotlinTypeMetadata boundedType, KotlinTypeMetadata upperBound)
    {
        before(upperBound);
        this.kotlinTypeVisitor.visitTypeUpperBound(clazz, boundedType, upperBound);
        after(upperBound);
    }


    @Override
    public void visitAbbreviation(Clazz              clazz,
                                  KotlinTypeMetadata kotlinTypeMetadata,
                                  KotlinTypeMetadata abbreviation)
    {
        before(kotlinTypeMetadata);
        this.kotlinTypeVisitor.visitAbbreviation(clazz, kotlinTypeMetadata, abbreviation);
        after(abbreviation);
    }


    @Override
    public void visitParameterUpperBound(Clazz                       clazz,
                                         KotlinTypeParameterMetadata boundedTypeParameter,
                                         KotlinTypeMetadata          upperBound)
    {
        before(upperBound);
        this.kotlinTypeVisitor.visitParameterUpperBound(clazz, boundedTypeParameter, upperBound);
        after(upperBound);
    }


    @Override
    public void visitTypeOfIsExpression(Clazz                          clazz,
                                        KotlinEffectExpressionMetadata kotlinEffectExprMetadata,
                                        KotlinTypeMetadata             typeOfIs)
    {
        before(typeOfIs);
        this.kotlinTypeVisitor.visitTypeOfIsExpression(clazz, kotlinEffectExprMetadata, typeOfIs);
        after(typeOfIs);
    }


    @Override
    public void visitTypeArgument(Clazz              clazz,
                                  KotlinTypeMetadata kotlinTypeMetadata,
                                  KotlinTypeMetadata typeArgument)
    {
        before(typeArgument);
        this.kotlinTypeVisitor.visitTypeArgument(clazz, kotlinTypeMetadata, typeArgument);
        after(typeArgument);
    }


    @Override
    public void visitStarProjection(Clazz clazz, KotlinTypeMetadata typeWithStarArg)
    {
        before(typeWithStarArg);
        this.kotlinTypeVisitor.visitStarProjection(clazz, typeWithStarArg);
        after(typeWithStarArg);
    }


    @Override
    public void visitOuterClass(Clazz clazz, KotlinTypeMetadata innerClass, KotlinTypeMetadata outerClass)
    {
        before(outerClass);
        this.kotlinTypeVisitor.visitOuterClass(clazz, innerClass, outerClass);
        after(outerClass);
    }


    @Override
    public void visitSuperType(Clazz                   clazz,
                               KotlinClassKindMetadata kotlinMetadata,
                               KotlinTypeMetadata      kotlinTypeMetadata)
    {
        before(kotlinTypeMetadata);
        this.kotlinTypeVisitor.visitSuperType(clazz, kotlinMetadata, kotlinTypeMetadata);
        after(kotlinTypeMetadata);
    }


    @Override
    public void visitConstructorValParamType(Clazz                              clazz,
                                             KotlinDeclarationContainerMetadata kotlinDeclarationContainerMetadata,
                                             KotlinConstructorMetadata          kotlinConstructorMetadata,
                                             KotlinValueParameterMetadata       kotlinValueParameterMetadata,
                                             KotlinTypeMetadata                 kotlinTypeMetadata)
    {
        before(kotlinTypeMetadata);
        this.kotlinTypeVisitor.visitConstructorValParamType(clazz,
                                                            kotlinDeclarationContainerMetadata,
                                                            kotlinConstructorMetadata,
                                                            kotlinValueParameterMetadata,
                                                            kotlinTypeMetadata);
        after(kotlinTypeMetadata);
    }


    @Override
    public void visitConstructorValParamVarArgType(Clazz                              clazz,
                                                   KotlinDeclarationContainerMetadata kotlinDeclarationContainerMetadata,
                                                   KotlinConstructorMetadata          kotlinConstructorMetadata,
                                                   KotlinValueParameterMetadata       kotlinValueParameterMetadata,
                                                   KotlinTypeMetadata                 kotlinTypeMetadata)
    {
        before(kotlinTypeMetadata);
        this.kotlinTypeVisitor.visitConstructorValParamVarArgType(clazz,
                                                                  kotlinDeclarationContainerMetadata,
                                                                  kotlinConstructorMetadata,
                                                                  kotlinValueParameterMetadata,
                                                                  kotlinTypeMetadata);
        after(kotlinTypeMetadata);
    }


    @Override
    public void visitPropertyType(Clazz                              clazz,
                                  KotlinDeclarationContainerMetadata kotlinDeclarationContainerMetadata,
                                  KotlinPropertyMetadata             kotlinPropertyMetadata,
                                  KotlinTypeMetadata                 kotlinTypeMetadata)
    {
        before(kotlinTypeMetadata);
        this.kotlinTypeVisitor.visitPropertyType(clazz,
                                                 kotlinDeclarationContainerMetadata,
                                                 kotlinPropertyMetadata,
                                                 kotlinTypeMetadata);
        after(kotlinTypeMetadata);
    }


    @Override
    public void visitPropertyReceiverType(Clazz                              clazz,
                                          KotlinDeclarationContainerMetadata kotlinDeclarationContainerMetadata,
                                          KotlinPropertyMetadata             kotlinPropertyMetadata,
                                          KotlinTypeMetadata                 kotlinTypeMetadata)
    {
        before(kotlinTypeMetadata);
        this.kotlinTypeVisitor.visitPropertyReceiverType(clazz,
                                                         kotlinDeclarationContainerMetadata,
                                                         kotlinPropertyMetadata,
                                                         kotlinTypeMetadata);
        after(kotlinTypeMetadata);
    }


    @Override
    public void visitPropertyValParamType(Clazz                              clazz,
                                          KotlinDeclarationContainerMetadata kotlinDeclarationContainerMetadata,
                                          KotlinPropertyMetadata             kotlinPropertyMetadata,
                                          KotlinValueParameterMetadata       kotlinValueParameterMetadata,
                                          KotlinTypeMetadata                 kotlinTypeMetadata)
    {
        before(kotlinTypeMetadata);
        this.kotlinTypeVisitor.visitPropertyValParamType(clazz,
                                                         kotlinDeclarationContainerMetadata,
                                                         kotlinPropertyMetadata,
                                                         kotlinValueParameterMetadata,
                                                         kotlinTypeMetadata);
        after(kotlinTypeMetadata);
    }


    @Override
    public void visitPropertyValParamVarArgType(Clazz                              clazz,
                                                KotlinDeclarationContainerMetadata kotlinDeclarationContainerMetadata,
                                                KotlinPropertyMetadata             kotlinPropertyMetadata,
                                                KotlinValueParameterMetadata       kotlinValueParameterMetadata,
                                                KotlinTypeMetadata                 kotlinTypeMetadata)
    {
        before(kotlinTypeMetadata);
        this.kotlinTypeVisitor.visitPropertyValParamVarArgType(clazz,
                                                               kotlinDeclarationContainerMetadata,
                                                               kotlinPropertyMetadata,
                                                               kotlinValueParameterMetadata,
                                                               kotlinTypeMetadata);
        after(kotlinTypeMetadata);
    }


    @Override
    public void visitFunctionReturnType(Clazz                  clazz,
                                        KotlinMetadata         kotlinMetadata,
                                        KotlinFunctionMetadata kotlinFunctionMetadata,
                                        KotlinTypeMetadata     kotlinTypeMetadata)
    {
        before(kotlinTypeMetadata);
        this.kotlinTypeVisitor.visitFunctionReturnType(clazz,
                                                       kotlinMetadata,
                                                       kotlinFunctionMetadata,
                                                       kotlinTypeMetadata);
        after(kotlinTypeMetadata);
    }


    @Override
    public void visitFunctionReceiverType(Clazz                  clazz,
                                          KotlinMetadata         kotlinMetadata,
                                          KotlinFunctionMetadata kotlinFunctionMetadata,
                                          KotlinTypeMetadata     kotlinTypeMetadata)
    {
        before(kotlinTypeMetadata);
        this.kotlinTypeVisitor.visitFunctionReceiverType(clazz,
                                                         kotlinMetadata,
                                                         kotlinFunctionMetadata,
                                                         kotlinTypeMetadata);
        after(kotlinTypeMetadata);
    }


    @Override
    public void visitFunctionValParamType(Clazz                        clazz,
                                          KotlinMetadata               kotlinMetadata,
                                          KotlinFunctionMetadata       kotlinFunctionMetadata,
                                          KotlinValueParameterMetadata kotlinValueParameterMetadata,
                                          KotlinTypeMetadata           kotlinTypeMetadata)
    {
        before(kotlinTypeMetadata);
        this.kotlinTypeVisitor.visitFunctionValParamType(clazz,
                                                         kotlinMetadata,
                                                         kotlinFunctionMetadata,
                                                         kotlinValueParameterMetadata,
                                                         kotlinTypeMetadata);
        after(kotlinTypeMetadata);
    }


    @Override
    public void visitFunctionValParamVarArgType(Clazz                        clazz,
                                                KotlinMetadata               kotlinMetadata,
                                                KotlinFunctionMetadata       kotlinFunctionMetadata,
                                                KotlinValueParameterMetadata kotlinValueParameterMetadata,
                                                KotlinTypeMetadata           kotlinTypeMetadata)
    {
        before(kotlinTypeMetadata);
        this.kotlinTypeVisitor.visitFunctionValParamVarArgType(clazz,
                                                               kotlinMetadata,
                                                               kotlinFunctionMetadata,
                                                               kotlinValueParameterMetadata,
                                                               kotlinTypeMetadata);
        after(kotlinTypeMetadata);
    }


    @Override
    public void visitAliasUnderlyingType(Clazz                              clazz,
                                         KotlinDeclarationContainerMetadata kotlinDeclarationContainerMetadata,
                                         KotlinTypeAliasMetadata            kotlinTypeAliasMetadata,
                                         KotlinTypeMetadata                 underlyingType)
    {
        before(underlyingType);
        this.kotlinTypeVisitor.visitAliasUnderlyingType(clazz,
                                                        kotlinDeclarationContainerMetadata,
                                                        kotlinTypeAliasMetadata,
                                                        underlyingType);
        after(underlyingType);
    }


    @Override
    public void visitAliasExpandedType(Clazz                              clazz,
                                       KotlinDeclarationContainerMetadata kotlinDeclarationContainerMetadata,
                                       KotlinTypeAliasMetadata            kotlinTypeAliasMetadata,
                                       KotlinTypeMetadata                 expandedType)
    {
        before(expandedType);
        this.kotlinTypeVisitor.visitAliasExpandedType(clazz,
                                                      kotlinDeclarationContainerMetadata,
                                                      kotlinTypeAliasMetadata,
                                                      expandedType);
        after(expandedType);
    }

    @Override
    public void visitAnyContextReceiverType(Clazz clazz, KotlinMetadata kotlinMetadata, KotlinTypeMetadata kotlinTypeMetadata)
    {
        before(kotlinTypeMetadata);
        this.kotlinTypeVisitor.visitAnyContextReceiverType(
            clazz, kotlinMetadata, kotlinTypeMetadata
        );
        after(kotlinTypeMetadata);
    }
}
