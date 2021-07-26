package proguard.kotlin.printer.visitor;

import proguard.classfile.Clazz;
import proguard.classfile.kotlin.KotlinClassKindMetadata;
import proguard.classfile.kotlin.KotlinMetadata;
import proguard.classfile.kotlin.visitor.KotlinMetadataVisitor;
import proguard.classfile.kotlin.visitor.KotlinTypeParameterVisitor;

/**
 * Delegates to the type parameters of a Kotlin class.
 *
 * @author James Hamilton
 */
public class KotlinClassTypeParameterVisitor implements KotlinMetadataVisitor
{

    private final KotlinTypeParameterVisitor delegate;

    public KotlinClassTypeParameterVisitor(KotlinTypeParameterVisitor delegate)
    {
        this.delegate = delegate;
    }

    @Override
    public void visitAnyKotlinMetadata(Clazz clazz, KotlinMetadata kotlinMetadata) { }

    @Override
    public void visitKotlinClassMetadata(Clazz clazz, KotlinClassKindMetadata kotlinClassKindMetadata)
    {
        kotlinClassKindMetadata.typeParametersAccept(clazz, this.delegate);
    }
}
