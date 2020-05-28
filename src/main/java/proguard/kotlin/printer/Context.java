/*
 * ProGuard -- shrinking, optimization, obfuscation, and preverification
 *             of Java bytecode.
 *
 * Copyright (c) 2002-2020 Guardsquare NV
 */

package proguard.kotlin.printer;

import proguard.classfile.Clazz;
import proguard.classfile.kotlin.KotlinClassKindMetadata;
import proguard.classfile.kotlin.KotlinMetadata;
import proguard.classfile.kotlin.KotlinTypeParameterMetadata;
import proguard.classfile.kotlin.visitor.KotlinTypeParameterVisitor;
import proguard.classfile.util.ClassUtil;

import java.util.HashMap;
import java.util.Stack;

import static proguard.classfile.kotlin.KotlinConstants.*;
import static proguard.classfile.util.ClassUtil.*;

/**
 * This class represents the context of the printer, in the form of a
 * stack of {@link ContextFrame}s. Each time a {@link KotlinMetadata} is
 * visited a context frame is pushed onto the stack and this way we can
 * keep track of the ancestors of the metadata that we're currently printing.
 *
 * @author James Hamilton
 */
public class Context
implements   KotlinTypeParameterVisitor
{

    private final Stack<ContextFrame>      contextFrameStack = new Stack<>();
    private final HashMap<Integer, String> typeParamIdMap    = new HashMap<>();

    private String packageName = "";

    public void push(ContextFrame contextFrame)
    {
        if (contextFrameStack.empty())
        {
            packageName = internalPackageName(contextFrame.clazz.getName());
            typeParamIdMap.clear();
        }

        contextFrameStack.push(contextFrame);

        if (previous().kotlinMetadataKind == METADATA_KIND_FILE_FACADE)
        {
            typeParamIdMap.clear();
        }

        if (contextFrame.kotlinMetadataKind == METADATA_KIND_MULTI_FILE_CLASS_PART &&
            previous().kotlinMetadataKind   == METADATA_KIND_MULTI_FILE_CLASS_FACADE)
        {
            typeParamIdMap.clear();
        }
    }


    public ContextFrame pop()
    {
        return contextFrameStack.pop();
    }


    public boolean isTop()
    {
        return contextFrameStack.size() == 1;
    }


    public String className(Clazz clazz, String dollarReplacement)
    {
        return className(clazz.getName(), dollarReplacement);
    }

    public String className(String className, String dollarReplacement)
    {
        if (className.length() == 0)
        {
            return "EmptyClassName /* Invalid metadata */";
        }

        String result = className;

        // If it's an inner class of this context, we can remove the prefix.
        for (int i = contextFrameStack.size() - 1; i >= 0; i--)
        {
            ContextFrame contextItem    = contextFrameStack.get(i);
            KotlinMetadata kotlinMetadata = contextItem.kotlinMetadata;

            if (kotlinMetadata.k == METADATA_KIND_CLASS)
            {
                KotlinClassKindMetadata kotlinClassKindMetadata = (KotlinClassKindMetadata)kotlinMetadata;
                if (className.startsWith(kotlinClassKindMetadata.className + "$"))
                {
                    result = result.replace(kotlinClassKindMetadata.className + "$", "");
                }
            }
            else if (kotlinMetadata.k == METADATA_KIND_FILE_FACADE)
            {
                result = result.replaceFirst("^" + contextItem.clazz.getName() + ".", "");
            }
        }

        // Remove the package prefix if it's the current one.
        if (result.equals(packageName + "/" + ClassUtil.internalShortClassName(className)))
        {
            result = result.replaceFirst("^" + packageName + "/", "");
        }
        // Replace default imports.
        result = result.replaceFirst(
            "^kotlin/(?:(?:annotation|collections|comparisons|io|ranges|sequences|test|jvm)/)?([^/]*)$",
            "$1");
        result = result.replaceFirst("^java/lang/([^/]*)$", "$1");
        // Replace $ with something valid.
        result = result.replace("$", dollarReplacement);
        // Convert to external format.
        result = externalClassName(result);
        // It's possible that the name is no longer valid i.e. it is a number
        if (!Character.isJavaIdentifierStart(result.charAt(0)))
        {
            result = "_" + result;
        }

        return result;
    }


    public ContextFrame previous()
    {
        int topMinus1 = contextFrameStack.size() - 2;
        if (topMinus1 < 0)
        {
            return ContextFrame.EMPTY_CONTEXT_FRAME;
        }

        return contextFrameStack.get(topMinus1);
    }


    public String getTypeParamName(int i)
    {
        return this.typeParamIdMap.getOrDefault(i, "X /* unknown " + i + " */");
    }


    public String getPackageName()
    {
        return packageName;
    }


    @Override
    public void visitAnyTypeParameter(Clazz clazz, KotlinTypeParameterMetadata kotlinTypeParameterMetadata)
    {
        // Use as a KotlinTypeParameter visitor to update the type parameter map.
        typeParamIdMap.put(kotlinTypeParameterMetadata.id, kotlinTypeParameterMetadata.name);
    }
}
