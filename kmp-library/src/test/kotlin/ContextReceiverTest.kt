import com.guardsquare.proguard.kotlin.printer.KotlinMetadataPrinter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import proguard.classfile.kotlin.visitor.ReferencedKotlinMetadataVisitor
import proguard.testutils.ClassPoolBuilder
import proguard.testutils.KotlinSource

class ContextReceiverTest : FunSpec({
    test("Context receivers should be printed") {
        val (programClassPool, _) = ClassPoolBuilder.fromSource(
            KotlinSource(
                "Test.kt",
                """
            interface LoggerContext
            interface FooBar
            
            context(LoggerContext, FooBar)
            fun foo() { }
            
            context(LoggerContext, FooBar)
            class Foo {
                context(LoggerContext)
                val prop: String
                    get() { return "String"; }
            }
                """.trimIndent()
            ),
            kotlincArguments = listOf("-Xcontext-receivers")
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
            * Kotlin file facade (metadata version 2.1.0).
            * From Java class: TestKt
            */

            // Functions

            context(LoggerContext, FooBar)
            fun foo() { }
        """.trimIndent()
        val fooMetadata = programClassPool.getClass("Foo").processingInfo as String

        fooMetadata.trimEnd() shouldBe """
           /**
           * Kotlin class (metadata version 2.1.0).
           * From Java class: Foo
           */
           context(LoggerContext, FooBar)
           class Foo {
           
               // Properties
           
               context(LoggerContext)
               val prop: String
                   get // getter method: public final java.lang.String getProp(LoggerContext)
           }
        """.trimIndent()
    }
})
