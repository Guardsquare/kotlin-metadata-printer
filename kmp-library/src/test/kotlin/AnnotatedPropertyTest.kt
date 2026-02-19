import com.guardsquare.proguard.kotlin.printer.KotlinMetadataPrinter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import proguard.classfile.kotlin.visitor.ReferencedKotlinMetadataVisitor
import proguard.testutils.ClassPoolBuilder
import proguard.testutils.KotlinSource
import util.kotlinVersion

class AnnotatedPropertyTest : FunSpec({
    test("Annotation on getter") {
        val (programClassPool, _) = ClassPoolBuilder.fromSource(
            KotlinSource(
                "Test.kt",
                """
                @get:JvmName("propertyJ")
                val property = 1
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

        val testKtMetadata = programClassPool.getClass("TestKt").processingInfo as String

        testKtMetadata.trimEnd() shouldBe """
            /**
            * Kotlin file facade (metadata version $kotlinVersion).
            * From Java class: TestKt
            */
            
            // Properties
            
            val property: Int
                // backing field: int property
                @JvmName(name = "propertyJ")
                get // getter method: public static final int propertyJ()
        """.trimIndent()
    }
})
