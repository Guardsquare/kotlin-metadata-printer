import com.guardsquare.proguard.kotlin.printer.KotlinMetadataPrinter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import proguard.classfile.kotlin.visitor.ReferencedKotlinMetadataVisitor
import proguard.testutils.ClassPoolBuilder
import proguard.testutils.KotlinSource

class AnnotationClassTest : FunSpec({
    test("Annotation class") {
        val (programClassPool, _) = ClassPoolBuilder.fromSource(
            KotlinSource(
                "MyAnnotation.kt",
                """
                annotation class MyAnnotation(val parameter1: String, val parameter2: String)
                """.trimIndent()
            ),
        )

        programClassPool.classesAccept(
            ReferencedKotlinMetadataVisitor(
                KotlinMetadataPrinter(
                    programClassPool
                )
            )
        )

        val testKtMetadata = programClassPool.getClass("MyAnnotation").processingInfo as String

        testKtMetadata.trimEnd() shouldBe """
            /**
            * Kotlin class (metadata version 1.7.1).
            * From Java class: MyAnnotation
            */
            @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
            annotation class MyAnnotation(val parameter1: String, val parameter2: String)
        """.trimIndent()
    }
})
