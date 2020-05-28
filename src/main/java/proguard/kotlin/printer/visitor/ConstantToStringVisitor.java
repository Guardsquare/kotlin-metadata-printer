/*
 * ProGuard -- shrinking, optimization, obfuscation, and preverification
 *             of Java bytecode.
 *
 * Copyright (c) 2002-2020 Guardsquare NV
 */

package proguard.kotlin.printer.visitor;

import proguard.classfile.Clazz;
import proguard.classfile.constant.*;
import proguard.classfile.constant.visitor.ConstantVisitor;

import java.util.function.Consumer;

/**
 * This {@link ConstantVisitor} visits all constants and passes their string representation
 * to the given string consumer function.
 *
 * @author James Hamilton
 */
public class ConstantToStringVisitor
implements   ConstantVisitor
{

    private final Consumer<String> stringConsumer;


    public ConstantToStringVisitor(Consumer<String> stringConsumer)
    {
        this.stringConsumer = stringConsumer;
    }


    @Override
    public void visitAnyConstant(Clazz clazz, Constant constant)
    {
        this.stringConsumer.accept("constant (" + constant.getClass() + ")");
    }


    @Override
    public void visitClassConstant(Clazz clazz, ClassConstant classConstant)
    {
        this.stringConsumer.accept(classConstant.getName(clazz));
    }


    @Override
    public void visitIntegerConstant(Clazz clazz, IntegerConstant integerConstant)
    {
        this.stringConsumer.accept(String.valueOf(integerConstant.getValue()));
    }


    @Override
    public void visitLongConstant(Clazz clazz, LongConstant longConstant)
    {
        this.stringConsumer.accept(String.valueOf(longConstant.getValue()));
    }


    @Override
    public void visitFloatConstant(Clazz clazz, FloatConstant floatConstant)
    {
        this.stringConsumer.accept(String.valueOf(floatConstant.getValue()));
    }


    @Override
    public void visitDoubleConstant(Clazz clazz, DoubleConstant doubleConstant)
    {
        this.stringConsumer.accept(String.valueOf(doubleConstant.getValue()));
    }


    @Override
    public void visitPrimitiveArrayConstant(Clazz clazz, PrimitiveArrayConstant primitiveArrayConstant)
    {
        this.stringConsumer.accept(String.valueOf(primitiveArrayConstant.getValues().toString()));
    }


    @Override
    public void visitStringConstant(Clazz clazz, StringConstant stringConstant)
    {
        this.stringConsumer.accept("\"" + stringConstant.getString(clazz) + "\"");
    }


    @Override
    public void visitUtf8Constant(Clazz clazz, Utf8Constant utf8Constant)
    {
        this.stringConsumer.accept("\"" + utf8Constant.getString() + "\"");
    }
}
