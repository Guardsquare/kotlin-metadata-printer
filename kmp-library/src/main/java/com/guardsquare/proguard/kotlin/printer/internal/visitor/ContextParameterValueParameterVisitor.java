package com.guardsquare.proguard.kotlin.printer.internal.visitor;

import proguard.classfile.Clazz;
import proguard.classfile.kotlin.*;
import proguard.classfile.kotlin.visitor.KotlinTypeVisitor;
import proguard.classfile.kotlin.visitor.KotlinValueParameterVisitor;

import java.util.function.BiConsumer;

/**
 * This KotlinTypeVisitor wraps another type visitor and executes the
 * provided functions before and after.
 *
 * @author James Hamilton
 */
public class ContextParameterValueParameterVisitor
implements KotlinValueParameterVisitor {
    private final KotlinValueParameterVisitor kotlinValueParameterVisitor;
    private final BiConsumer<Integer, KotlinValueParameterMetadata> before;
    private final BiConsumer<Integer, KotlinValueParameterMetadata> after;
    private       int                                     i = 0;

    public ContextParameterValueParameterVisitor(BiConsumer<Integer, KotlinValueParameterMetadata> before, KotlinValueParameterVisitor kotlinValueParameterVisitor, BiConsumer<Integer, KotlinValueParameterMetadata> after) {
        this.kotlinValueParameterVisitor = kotlinValueParameterVisitor;
        this.before = before;
        this.after = after;
    }

    private void before(KotlinValueParameterMetadata kotlinValueParameterMetadata)
    {
        this.before.accept(i, kotlinValueParameterMetadata);
    }

    private void after(KotlinValueParameterMetadata kotlinValueParameterMetadata)
    {
        if (this.after != null)
        {
            this.after.accept(i, kotlinValueParameterMetadata);
        }
        i++;
    }

    @Override
    public void visitAnyValueParameter(Clazz clazz, KotlinValueParameterMetadata kotlinValueParameterMetadata) {
        before(kotlinValueParameterMetadata);
        kotlinValueParameterVisitor.visitAnyValueParameter(clazz, kotlinValueParameterMetadata);
        after(kotlinValueParameterMetadata);
    }

    @Override
    public void visitConstructorValParameter(Clazz clazz, KotlinClassKindMetadata kotlinClassKindMetadata, KotlinConstructorMetadata kotlinConstructorMetadata, KotlinValueParameterMetadata kotlinValueParameterMetadata) {
        before(kotlinValueParameterMetadata);
        kotlinValueParameterVisitor.visitConstructorValParameter(clazz, kotlinClassKindMetadata, kotlinConstructorMetadata, kotlinValueParameterMetadata);
        after(kotlinValueParameterMetadata);
    }

    @Override
    public void visitFunctionValParameter(Clazz clazz, KotlinMetadata kotlinMetadata, KotlinFunctionMetadata kotlinFunctionMetadata, KotlinValueParameterMetadata kotlinValueParameterMetadata) {
        before(kotlinValueParameterMetadata);
        kotlinValueParameterVisitor.visitFunctionValParameter(clazz, kotlinMetadata, kotlinFunctionMetadata, kotlinValueParameterMetadata);
        after(kotlinValueParameterMetadata);
    }

    @Override
    public void visitPropertyValParameter(Clazz clazz, KotlinDeclarationContainerMetadata kotlinDeclarationContainerMetadata, KotlinPropertyMetadata kotlinPropertyMetadata, KotlinValueParameterMetadata kotlinValueParameterMetadata) {
        before(kotlinValueParameterMetadata);
        kotlinValueParameterVisitor.visitPropertyValParameter(clazz, kotlinDeclarationContainerMetadata, kotlinPropertyMetadata, kotlinValueParameterMetadata);
        after(kotlinValueParameterMetadata);
    }

    @Override
    public void visitAnyContextParameter(Clazz clazz, KotlinMetadata kotlinMetadata, KotlinValueParameterMetadata kotlinValueParameterMetadata) {
        before(kotlinValueParameterMetadata);
        kotlinValueParameterVisitor.visitAnyContextParameter(clazz, kotlinMetadata, kotlinValueParameterMetadata);
        after(kotlinValueParameterMetadata);
    }

    @Override
    public void visitFunctionContextParameter(Clazz clazz, KotlinMetadata kotlinMetadata, KotlinFunctionMetadata kotlinFunctionMetadata, KotlinValueParameterMetadata kotlinValueParameterMetadata) {
        before(kotlinValueParameterMetadata);
        kotlinValueParameterVisitor.visitFunctionContextParameter(clazz, kotlinMetadata, kotlinFunctionMetadata, kotlinValueParameterMetadata);
        after(kotlinValueParameterMetadata);
    }

    @Override
    public void visitPropertyContextParameter(Clazz clazz, KotlinDeclarationContainerMetadata kotlinDeclarationContainerMetadata, KotlinPropertyMetadata kotlinPropertyMetadata, KotlinValueParameterMetadata kotlinValueParameterMetadata) {
        before(kotlinValueParameterMetadata);
        kotlinValueParameterVisitor.visitPropertyContextParameter(clazz, kotlinDeclarationContainerMetadata, kotlinPropertyMetadata, kotlinValueParameterMetadata);
        after(kotlinValueParameterMetadata);
    }
}
