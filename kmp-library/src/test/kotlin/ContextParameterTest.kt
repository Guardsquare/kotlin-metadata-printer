import com.guardsquare.proguard.kotlin.printer.KotlinMetadataPrinter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import proguard.classfile.kotlin.visitor.ReferencedKotlinMetadataVisitor
import proguard.testutils.ClassPoolBuilder
import proguard.testutils.KotlinSource
import util.kotlinVersion

class ContextParameterTest : FunSpec({
    test("Context parameters should be printed") {
        val (programClassPool, _) = ClassPoolBuilder.fromSource(
            KotlinSource(
                "Test.kt",
                """
            interface LoggerContext
            interface FooBar
            
            context(loggerContext: LoggerContext, fooBar: FooBar)
            fun foo() { }

            class Foo {
                context(loggerContext: LoggerContext)
                val prop: String
                    get() { return "String"; }
            }
                """.trimIndent()
            ),
            kotlincArguments = listOf("-Xcontext-parameters")
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

            // Functions

            context(loggerContext: LoggerContext, fooBar: FooBar)
            fun foo() { }
        """.trimIndent()
        val fooMetadata = programClassPool.getClass("Foo").processingInfo as String

        fooMetadata.trimEnd() shouldBe """
           /**
           * Kotlin class (metadata version $kotlinVersion).
           * From Java class: Foo
           */
           class Foo {
           
               // Properties
           
               context(loggerContext: LoggerContext)
               val prop: String
                   get // getter method: public final java.lang.String getProp(LoggerContext)
           }
        """.trimIndent()
    }
})
