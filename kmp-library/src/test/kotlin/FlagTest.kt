import com.guardsquare.proguard.kotlin.printer.KotlinMetadataPrinter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import proguard.classfile.kotlin.visitor.ReferencedKotlinMetadataVisitor
import proguard.testutils.ClassPoolBuilder
import proguard.testutils.KotlinSource

class FlagTest : FunSpec({
    test("IsDefinitelyNonNull") {
        val (programClassPool, _) = ClassPoolBuilder.fromSource(
            KotlinSource(
                "Test.kt",
                """
            fun <T> elvisLike(x: T, y: T & Any): T & Any = x ?: y
                """
            )
        )

        programClassPool.classesAccept(
            ReferencedKotlinMetadataVisitor(
                KotlinMetadataPrinter(
                    programClassPool
                )
            )
        )

        val testKtMetadata = programClassPool.getClass("TestKt").processingInfo as String
        println(testKtMetadata)

        testKtMetadata.trimEnd() shouldBe """
        /**
        * Kotlin file facade (metadata version 2.1.0).
        * From Java class: TestKt
        */
        
        // Functions
        
        @SinceKotlin("1.7.0")
        fun <T> elvisLike(x: T, y: T & Any): T & Any { }
        """.trimIndent()
    }
})
