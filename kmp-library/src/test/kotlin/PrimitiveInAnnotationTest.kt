import com.guardsquare.proguard.kotlin.printer.KotlinMetadataPrinter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import proguard.classfile.kotlin.visitor.ReferencedKotlinMetadataVisitor
import proguard.testutils.ClassPoolBuilder
import proguard.testutils.KotlinSource

class PrimitiveInAnnotationTest : FunSpec({
    test("Primitive type class in annotation") {
        val (programClassPool, _) = ClassPoolBuilder.fromSource(
            KotlinSource(
                "Test.kt",
                """
                import kotlin.reflect.KClass

                @Target(AnnotationTarget.CLASS)
                @Retention(AnnotationRetention.RUNTIME)
                annotation class Serializer(val forClass: KClass<*>)
                
                @Serializer(forClass = Int::class)
                class Test {
                    var myInt: Int = 0
                }
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

        val testKtMetadata = programClassPool.getClass("Test").processingInfo as String

        testKtMetadata.trimEnd() shouldBe """
            /**
            * Kotlin class (metadata version 2.0.0).
            * From Java class: Test
            */
            @Serializer(forClass = Int::class)
            class Test {

                // Properties

                var myInt: Int
                    // backing field: int myInt
                    get // getter method: public final int getMyInt()
                    set() // setter method: public final void setMyInt(int)
            }
        """.trimIndent()
    }
})
