/*
 * ProGuard -- shrinking, optimization, obfuscation, and preverification
 *             of Java bytecode.
 *
 * Copyright (c) 2002-2020 Guardsquare NV
 */

package com.guardsquare.proguard.kotlin.printer.internal;

import proguard.classfile.Clazz;
import proguard.classfile.kotlin.KotlinFunctionMetadata;
import proguard.classfile.kotlin.KotlinMetadata;

/**
 * @author James Hamilton
 */
public class ContextFrame
{
    public static final ContextFrame EMPTY_CONTEXT_FRAME = new ContextFrame();

    public final Clazz                  clazz;
    public final KotlinMetadata         kotlinMetadata;
    public final int                    kotlinMetadataKind;
    public final KotlinFunctionMetadata kotlinFunctionMetadata;


    private ContextFrame()
    {
        this(null, null, null);
    }


    public ContextFrame(Clazz clazz, KotlinMetadata kotlinMetadata)
    {
        this(clazz, kotlinMetadata, null);
    }


    public ContextFrame(Clazz clazz, KotlinMetadata kotlinMetadata, KotlinFunctionMetadata kotlinFunctionMetadata)
    {
        this.clazz                  = clazz;
        this.kotlinMetadata         = kotlinMetadata;
        this.kotlinMetadataKind     = kotlinMetadata != null ? kotlinMetadata.k : -1;
        this.kotlinFunctionMetadata = kotlinFunctionMetadata;
    }
}
