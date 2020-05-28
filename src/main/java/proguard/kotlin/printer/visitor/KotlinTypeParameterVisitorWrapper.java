/*
 * ProGuard -- shrinking, optimization, obfuscation, and preverification
 *             of Java bytecode.
 *
 * Copyright (c) 2002-2020 Guardsquare NV
 */

package proguard.kotlin.printer.visitor;

import proguard.classfile.Clazz;
import proguard.classfile.kotlin.*;
import proguard.classfile.kotlin.visitor.KotlinTypeParameterVisitor;

import java.util.function.BiConsumer;

/**
 * This KotlinTypeParameterVisitor wraps another type visitor and executes the
 * provided functions before and after.
 *
 * @author James Hamilton
 */
public class KotlinTypeParameterVisitorWrapper
implements   KotlinTypeParameterVisitor
{
    private final KotlinTypeParameterVisitor                       kotlinTypeParameterVisitor;
    private final BiConsumer<Integer, KotlinTypeParameterMetadata> before;
    private final BiConsumer<Integer, KotlinTypeParameterMetadata> after;
    private       int                                              i = 0;


    public KotlinTypeParameterVisitorWrapper(BiConsumer<Integer, KotlinTypeParameterMetadata> before,
                                             KotlinTypeParameterVisitor                       kotlinTypeParameterVisitor,
                                             BiConsumer<Integer, KotlinTypeParameterMetadata> after)
    {
        this.kotlinTypeParameterVisitor = kotlinTypeParameterVisitor;
        this.before = before;
        this.after = after;
    }


    public KotlinTypeParameterVisitorWrapper(BiConsumer<Integer, KotlinTypeParameterMetadata> before,
                                             KotlinTypeParameterVisitor                       kotlinTypeParameterVisitor)
    {
        this(before, kotlinTypeParameterVisitor, null);
    }


    private void before(KotlinTypeParameterMetadata kotlinTypeParameterMetadata)
    {
        this.before.accept(i, kotlinTypeParameterMetadata);
    }


    private void after(KotlinTypeParameterMetadata kotlinTypeParameterMetadata)
    {
        if (this.after != null)
        {
            this.after.accept(i, kotlinTypeParameterMetadata);
        }
        i++;
    }


    @Override
    public void visitAnyTypeParameter(Clazz clazz, KotlinTypeParameterMetadata kotlinTypeParameterMetadata) { }


    @Override
    public void visitClassTypeParameter(Clazz                       clazz,
                                        KotlinMetadata              kotlinMetadata,
                                        KotlinTypeParameterMetadata kotlinTypeParameterMetadata)
    {
        before(kotlinTypeParameterMetadata);
        kotlinTypeParameterVisitor.visitClassTypeParameter(clazz, kotlinMetadata, kotlinTypeParameterMetadata);
        after(kotlinTypeParameterMetadata);
    }


    @Override
    public void visitPropertyTypeParameter(Clazz                              clazz,
                                           KotlinDeclarationContainerMetadata kotlinDeclarationContainerMetadata,
                                           KotlinPropertyMetadata             kotlinPropertyMetadata,
                                           KotlinTypeParameterMetadata        kotlinTypeParameterMetadata)
    {
        before(kotlinTypeParameterMetadata);
        kotlinTypeParameterVisitor.visitPropertyTypeParameter(clazz,
                                                              kotlinDeclarationContainerMetadata,
                                                              kotlinPropertyMetadata,
                                                              kotlinTypeParameterMetadata);
        after(kotlinTypeParameterMetadata);
    }


    @Override
    public void visitFunctionTypeParameter(Clazz                       clazz,
                                           KotlinMetadata              kotlinMetadata,
                                           KotlinFunctionMetadata      kotlinFunctionMetadata,
                                           KotlinTypeParameterMetadata kotlinTypeParameterMetadata)
    {
        before(kotlinTypeParameterMetadata);
        kotlinTypeParameterVisitor.visitFunctionTypeParameter(clazz,
                                                              kotlinMetadata,
                                                              kotlinFunctionMetadata,
                                                              kotlinTypeParameterMetadata);
        after(kotlinTypeParameterMetadata);
    }


    @Override
    public void visitAliasTypeParameter(Clazz                              clazz,
                                        KotlinDeclarationContainerMetadata kotlinDeclarationContainerMetadata,
                                        KotlinTypeAliasMetadata            kotlinTypeAliasMetadata,
                                        KotlinTypeParameterMetadata        kotlinTypeParameterMetadata)
    {
        before(kotlinTypeParameterMetadata);
        kotlinTypeParameterVisitor.visitAliasTypeParameter(clazz,
                                                           kotlinDeclarationContainerMetadata,
                                                           kotlinTypeAliasMetadata,
                                                           kotlinTypeParameterMetadata);
        after(kotlinTypeParameterMetadata);
    }
}
