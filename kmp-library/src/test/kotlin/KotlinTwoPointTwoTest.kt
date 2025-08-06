import com.guardsquare.proguard.kotlin.printer.KotlinMetadataPrinter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import proguard.classfile.kotlin.visitor.ReferencedKotlinMetadataVisitor
import proguard.testutils.ClassPoolBuilder
import proguard.testutils.KotlinSource

class KotlinTwoPointTwoTest : FunSpec({
    test("Non-local break & continue") {
        val (programClassPool, _) = ClassPoolBuilder.fromSource(
            KotlinSource(
                "Test.kt",
                """
                fun processList(elements: List<Int>): Boolean {
                    for (element in elements) {
                        val variable = element ?: run {
                            continue
                        }
                        if (variable == 0) return true
                    }
                    return false
                }
                """.trimIndent(),
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
        * Kotlin file facade (metadata version 2.2.0).
        * From Java class: TestKt
        */
        
        // Functions
        
        fun processList(elements: List<Int>): Boolean { }
        """.trimIndent()
    }
    test("Nested type aliases") {
        val (programClassPool, _) = ClassPoolBuilder.fromSource(
            KotlinSource(
                "Test.kt",
                """
                class Container {
                    typealias ContainerSet = Set<Container>
                }

                """.trimIndent(),
            ),
            kotlincArguments = listOf("-Xnested-type-aliases")
        )

        programClassPool.classesAccept(
            ReferencedKotlinMetadataVisitor(
                KotlinMetadataPrinter(
                    programClassPool
                )
            )
        )

        val containerMetadata = programClassPool.getClass("Container").processingInfo as String
        println(containerMetadata)

        containerMetadata.trimEnd() shouldBe """
        /**
        * Kotlin class (metadata version 2.2.0).
        * From Java class: Container
        */
        class Container {
        
            // Type aliases
        
            typealias ContainerSet = Set<Container>
        }
        """.trimIndent()
    }
    test("RequiresOptIn annotations") {
        val (programClassPool, _) = ClassPoolBuilder.fromSource(
            KotlinSource(
                "Test.kt",
                """
                @RequiresOptIn(
                level = RequiresOptIn.Level.WARNING,
                message = "Interfaces in this library are experimental"
                )
                annotation class UnstableApi()
                
                @SubclassOptInRequired(UnstableApi::class)
                interface CoreLibraryApi
                """.trimIndent(),
            ),
        )

        programClassPool.classesAccept(
            ReferencedKotlinMetadataVisitor(
                KotlinMetadataPrinter(
                    programClassPool
                )
            )
        )
        val coreLibraryApiMetadata = programClassPool.getClass("CoreLibraryApi").processingInfo as String
        println(coreLibraryApiMetadata)

        coreLibraryApiMetadata.trimEnd() shouldBe """
        /**
        * Kotlin class (metadata version 2.2.0).
        * From Java class: CoreLibraryApi
        */
        @SubclassOptInRequired(markerClass = {UnstableApi})
        interface CoreLibraryApi
        """.trimIndent()

        val unstableApiMetadata = programClassPool.getClass("UnstableApi").processingInfo as String
        println(unstableApiMetadata)

        unstableApiMetadata.trimEnd() shouldBe """
        /**
        * Kotlin class (metadata version 2.2.0).
        * From Java class: UnstableApi
        */
        @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
        @RequiresOptIn(message = "Interfaces in this library are experimental", level = RequiresOptIn.Level.WARNING)
        annotation class UnstableApi
        """.trimIndent()
    }
    test("Guard conditions in when with a subject") {
        val (programClassPool, _) = ClassPoolBuilder.fromSource(
            KotlinSource(
                "Test.kt",
                """
                sealed interface Animal {
                    data class Cat(val mouseHunter: Boolean) : Animal {
                        fun feedCat() {}
                    }
                
                    data class Dog(val breed: String) : Animal {
                        fun feedDog() {}
                    }
                }
                
                fun feedAnimal(animal: Animal) {
                    when (animal) {
                        is Animal.Dog -> animal.feedDog()
                        is Animal.Cat if !animal.mouseHunter -> animal.feedCat()
                        else -> println("Unknown animal")
                    }
                }
                """.trimIndent(),
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
        * Kotlin file facade (metadata version 2.2.0).
        * From Java class: TestKt
        */
        
        // Functions
        
        fun feedAnimal(animal: Animal) { }
        """.trimIndent()
    }
    test("Multi-dollar string interpolation") {
        val (programClassPool, _) = ClassPoolBuilder.fromSource(
            KotlinSource(
                "Test.kt",
                """
                class MultiDollar {
                    companion object {
                        val multiDollarString: String
                              get() = ${"$$"}${"\"\"\""}aMultiDollarString${"\"\"\""}
                    }
                }
                """.trimIndent(),
            )
        )

        programClassPool.classesAccept(
            ReferencedKotlinMetadataVisitor(
                KotlinMetadataPrinter(
                    programClassPool
                )
            )
        )

        val multiDollarMetadata = programClassPool.getClass("MultiDollar").processingInfo as String
        println(multiDollarMetadata)

        multiDollarMetadata.trimEnd() shouldBe """
        /**
        * Kotlin class (metadata version 2.2.0).
        * From Java class: MultiDollar
        */
        class MultiDollar {
        
            // Kotlin companion class from Java class: MultiDollar${"$"}Companion
            companion object {
            
                // Properties
        
                val multiDollarString: String
                    get // getter method: public final java.lang.String getMultiDollarString()
            }
        
            // Synthetic inner classes - these were generated by the Kotlin compiler from e.g. lambdas
        
            // Kotlin companion class from Java class: MultiDollar${"$"}Companion
            companion object {
            
                // Properties
        
                val multiDollarString: String
                    get // getter method: public final java.lang.String getMultiDollarString()
            }
        }
        """.trimIndent()

        val multiDollarCompanionMetadata = programClassPool.getClass("MultiDollar${"$"}Companion").processingInfo as String
        println(multiDollarCompanionMetadata)

        multiDollarCompanionMetadata.trimEnd() shouldBe """
        /**
        * Kotlin companion class (metadata version 2.2.0).
        * From Java class: MultiDollar${"$"}Companion
        */
        companion object MultiDollar_Companion {
        
            // Properties
        
            val multiDollarString: String
                get // getter method: public final java.lang.String getMultiDollarString()
        }
        """.trimIndent()
    }
})
