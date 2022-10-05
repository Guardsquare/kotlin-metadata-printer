package com.guardsquare.proguard.kotlin.printer.internal.visitor;

import proguard.classfile.Clazz;
import proguard.classfile.kotlin.KotlinMetadata;
import proguard.classfile.kotlin.visitor.KotlinMetadataVisitor;

import java.util.function.BiConsumer;

/**
 * This KotlinMetadataVisitor wraps another KotlinMetadataVisitor visitor and executes the
 * provided functions before and after.
 *
 * @author James Hamilton
 */
public class KotlinMetadataVisitorWrapper
implements   KotlinMetadataVisitor
{
    private final KotlinMetadataVisitor               kotlinMetadataVisitor;
    private final BiConsumer<Integer, KotlinMetadata> before;
    private final BiConsumer<Integer, KotlinMetadata> after;
    private       int                                 i = 0;


    public KotlinMetadataVisitorWrapper(BiConsumer<Integer, KotlinMetadata> before,
                                        KotlinMetadataVisitor               kotlinMetadataVisitor,
                                        BiConsumer<Integer, KotlinMetadata> after)
    {
        this.kotlinMetadataVisitor = kotlinMetadataVisitor;
        this.before                = before;
        this.after                 = after;
    }

    public KotlinMetadataVisitorWrapper(BiConsumer<Integer, KotlinMetadata> before,
                                        KotlinMetadataVisitor               kotlinMetadataVisitor)
    {
        this(before, kotlinMetadataVisitor, null);
    }


    private void before(KotlinMetadata kotlinMetadata)
    {
        this.before.accept(i, kotlinMetadata);
    }


    private void after(KotlinMetadata kotlinMetadata)
    {
        if (this.after != null)
        {
            this.after.accept(i, kotlinMetadata);
        }
        i++;
    }

    // Implementation for KotlinMetadataVisitor.

    @Override
    public void visitAnyKotlinMetadata(Clazz clazz, KotlinMetadata kotlinMetadata)
    {
        before(kotlinMetadata);
        kotlinMetadata.accept(clazz, this.kotlinMetadataVisitor);
        after(kotlinMetadata);
    }
}
