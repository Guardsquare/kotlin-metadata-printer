package proguard.kotlin.printer.visitor;

import proguard.classfile.Clazz;
import proguard.classfile.kotlin.*;
import proguard.classfile.kotlin.visitor.KotlinAnnotationVisitor;

import java.util.function.BiConsumer;

/**
 * This {@link KotlinAnnotationVisitor} wraps another annotation visitor and executes the
 * provided functions before and after.
 *
 * @author James Hamilton
 */
public class KotlinAnnotationVisitorWrapper
implements   KotlinAnnotationVisitor
{
    private final KotlinAnnotationVisitor                       kotlinAnnotationVisitor;
    private final BiConsumer<Integer, KotlinMetadataAnnotation> before;
    private final BiConsumer<Integer, KotlinMetadataAnnotation> after;
    private       int                                           i = 0;


    public KotlinAnnotationVisitorWrapper(BiConsumer<Integer, KotlinMetadataAnnotation> before,
                                          KotlinAnnotationVisitor                       kotlinAnnotationVisitor)
    {
        this(before, kotlinAnnotationVisitor, null);
    }

    public KotlinAnnotationVisitorWrapper(BiConsumer<Integer, KotlinMetadataAnnotation> before,
                                          KotlinAnnotationVisitor                       kotlinAnnotationVisitor,
                                          BiConsumer<Integer, KotlinMetadataAnnotation> after)
    {
        this.kotlinAnnotationVisitor = kotlinAnnotationVisitor;
        this.before                  = before;
        this.after                   = after;
    }

    private void before(KotlinMetadataAnnotation kotlinMetadataAnnotation)
    {
        this.before.accept(i, kotlinMetadataAnnotation);
    }


    private void after(KotlinMetadataAnnotation kotlinMetadataAnnotation)
    {
        if (this.after != null)
        {
            this.after.accept(i, kotlinMetadataAnnotation);
        }
        i++;
    }

    @Override
    public void visitAnyAnnotation(Clazz clazz, KotlinMetadataAnnotation annotation)
    {
        before(annotation);
    }


    @Override
    public void visitTypeAnnotation(Clazz                    clazz,
                                    KotlinTypeMetadata       kotlinTypeMetadata,
                                    KotlinMetadataAnnotation annotation)
    {
        before(annotation);
        annotation.accept(clazz, kotlinTypeMetadata, this.kotlinAnnotationVisitor);
        after(annotation);
    }


    @Override
    public void visitTypeParameterAnnotation(Clazz                       clazz,
                                             KotlinTypeParameterMetadata kotlinTypeParameterMetadata,
                                             KotlinMetadataAnnotation    annotation)
    {
        before(annotation);
        annotation.accept(clazz, kotlinTypeParameterMetadata, this.kotlinAnnotationVisitor);
        after(annotation);
    }


    @Override
    public void visitTypeAliasAnnotation(Clazz                    clazz,
                                         KotlinTypeAliasMetadata  kotlinTypeAliasMetadata,
                                         KotlinMetadataAnnotation annotation)
    {
        before(annotation);
        annotation.accept(clazz, kotlinTypeAliasMetadata, this.kotlinAnnotationVisitor);
        after(annotation);
    }
}
