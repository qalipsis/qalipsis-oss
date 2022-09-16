/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.api.processors

import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DelicateKotlinPoetApi
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.plusParameter
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.typeNameOf
import io.qalipsis.api.serialization.Serializable
import io.qalipsis.api.services.ServicesFiles
import java.nio.file.Path
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedOptions
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.MirroredTypesException
import javax.lang.model.util.Elements
import javax.tools.Diagnostic
import javax.tools.StandardLocation
import kotlin.io.path.Path
import kotlin.reflect.KClass


@ExperimentalStdlibApi
@DelicateKotlinPoetApi("Awareness of delicate aspect")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@SupportedAnnotationTypes(
    SerializationAnnotationProcessor.ANNOTATION_CLASS_NAME,
    SerializationAnnotationProcessor.SERIALIZABLE_CLASS_NAME
)
@SupportedOptions(
    SerializationAnnotationProcessor.KAPT_KOTLIN_GENERATED_OPTION_NAME
)
class SerializationAnnotationProcessor : AbstractProcessor() {

    companion object {

        const val ANNOTATION_CLASS_NAME = "io.qalipsis.api.serialization.Serializable"

        const val SERIALIZABLE_CLASS_NAME = "kotlinx.serialization.Serializable"

        const val SERIALIZERS_PATH = "META-INF/qalipsis/serializers"

        // Property pointing to the folder where Kapt generates sources.
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
    }

    private lateinit var typeUtils: TypeUtils

    private lateinit var elementUtils: Elements

    private lateinit var stringElememt: TypeElement

    /**
     * Path of the root folder when the Kotlin code has to be generated.
     */
    private lateinit var kaptKotlinGeneratedDir: Path

    /**
     * Qualified names of all the serializable classes.
     */
    private val allSerializers = mutableSetOf<String>()

    private var serializationStatements: SerializationStatements = JsonSerializationStatements

    override fun init(processingEnv: ProcessingEnvironment) {
        try {
            // If Protobuf is in the classpath, it has precedence to generate the wrapper.
            Class.forName("kotlinx.serialization.protobuf.ProtoBufBuilder")
            serializationStatements = ProtobufSerializationStatements
            processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, "Using Protobuf as serializer")
        } catch (ex: Exception) {
            // Otherwise, the JSON serialization is used as fallback.
            processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, "Using JSON as serializer")
        }

        super.init(processingEnv)
        elementUtils = processingEnv.elementUtils
        typeUtils = TypeUtils(processingEnv.elementUtils, processingEnv.typeUtils)
        stringElememt = elementUtils.getTypeElement("java.lang.String")
    }

    override fun process(annotations: MutableSet<out TypeElement>, roundEnv: RoundEnvironment): Boolean {
        kaptKotlinGeneratedDir =
            processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]?.let { Path(it) } ?: return false

        // Processes the classes having the native Kotlin annotation.
        roundEnv.getElementsAnnotatedWith(kotlinx.serialization.Serializable::class.java)
            .filter { it.kind == ElementKind.CLASS }
            .map { it as TypeElement }
            .forEach(this::createWrapper)

        // Processes the elements having the native Kotlin annotation.
        roundEnv.getElementsAnnotatedWith(Serializable::class.java).forEach(this::proceedQalipsisSerialization)

        // Updates the file with the list of serializers.
        if (roundEnv.processingOver()) {
            ServicesFiles.writeFile(
                allSerializers,
                processingEnv.filer.createResource(StandardLocation.CLASS_OUTPUT, "", SERIALIZERS_PATH)
                    .openOutputStream()
            )
            allSerializers.clear()
        }

        return true
    }

    private fun proceedQalipsisSerialization(annotatedType: Element) {
        val annotation = annotatedType.getAnnotation(Serializable::class.java)
        getSupportedTypes(annotation)
            .filter { element ->
                (element == stringElememt
                        || element.getAnnotationsByType(kotlinx.serialization.Serializable::class.java).isNotEmpty())
                    .also { serializable ->
                        if (!serializable) {
                            processingEnv.messager.printMessage(
                                Diagnostic.Kind.WARNING,
                                "Cannot generate the SerialFormatWrapper for the class $element, because it does not have the @kotlinx.serialization.Serializable annotation"
                            )
                        }
                    }
            }
            .forEach(this::createWrapper)
    }

    /**
     * Extract the list of types specified in the annotation [Serializable].
     */
    private fun getSupportedTypes(annotation: Serializable): Collection<TypeElement> {
        // Here, it is a bit tricky. It is not possible to get the classes, but the generated exception
        // "javax.lang.model.type.MirroredTypesException: Attempt to access Class objects for TypeMirrors" actually
        // contains the [TypeMirror]s we need. Long story here:
        // https://area-51.blog/2009/02/13/getting-class-values-from-annotations-in-an-annotationprocessor/
        return try {
            annotation.types
            null
        } catch (e: MirroredTypesException) {
            e.typeMirrors.mapNotNull(typeUtils::getTypeElement)
        } catch (e: Exception) {
            null
        }?.takeIf(Collection<*>::isNotEmpty) ?: emptyList()
    }

    private fun createWrapper(annotatedType: TypeElement) {
        val hasNoGeneric = annotatedType.typeParameters.isEmpty()
        if (hasNoGeneric) {
            val packageName = elementUtils.getPackageOf(annotatedType)
            val serializationWrapperClassName = "${annotatedType.simpleName}SerializationWrapper"
            generateKotlinWrapper(packageName, serializationWrapperClassName, annotatedType)
            allSerializers += "${packageName}.${serializationWrapperClassName}"
        } else {
            processingEnv.messager.printMessage(
                Diagnostic.Kind.WARNING,
                "Cannot generate the SerialFormatWrapper for the class $annotatedType, because it uses generics"
            )
        }
    }

    private fun generateKotlinWrapper(
        packageName: PackageElement,
        serializationWrapperClassName: String,
        annotatedType: TypeElement
    ) {
        processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, "Generating serialization wrapper for $annotatedType")
        val serializationWrapperClassFile = FileSpec.builder("$packageName", serializationWrapperClassName)
            .also { serializationStatements.addImport(it) }
        serializationWrapperClassFile.addType(
            TypeSpec.classBuilder(serializationWrapperClassName)
                .addAnnotation(ClassName.bestGuess("kotlinx.serialization.ExperimentalSerializationApi"))
                .addSuperinterface(
                    ClassName.bestGuess("io.qalipsis.api.serialization.SerialFormatWrapper")
                        .plusParameter(annotatedType.asClassName())
                )
                .addFunction(
                    FunSpec.builder("serialize")
                        .addModifiers(KModifier.OVERRIDE)
                        .addParameter(ParameterSpec.builder("entity", annotatedType.asClassName()).build())
                        .also { serializationStatements.addSerialization(it) }
                        .returns(ClassName("kotlin", "ByteArray"))
                        .build()

                )
                .addFunction(
                    FunSpec.builder("deserialize")
                        .addModifiers(KModifier.OVERRIDE)
                        .addParameter(ParameterSpec.builder("source", ClassName("kotlin", "ByteArray")).build())
                        .also { serializationStatements.addDeserialization(it) }
                        .returns(annotatedType.asClassName())
                        .build()

                )
                .addProperty(
                    PropertySpec.builder("types", ARRAY.plusParameter(typeNameOf<KClass<*>>()), KModifier.OVERRIDE)
                        .initializer(CodeBlock.of("""arrayOf(${annotatedType.asClassName()}::class)""")).build()
                )
                .addProperty(
                    PropertySpec.builder("qualifier", ClassName("kotlin", "String"), KModifier.OVERRIDE)
                        .initializer(CodeBlock.of(""""${serializationStatements.identifier}"""")).build()
                )
                .build()
        )
        serializationWrapperClassFile.build()
        serializationWrapperClassFile.build().writeTo(kaptKotlinGeneratedDir)
    }

    private interface SerializationStatements {
        fun addImport(builder: FileSpec.Builder): FileSpec.Builder
        fun addSerialization(builder: FunSpec.Builder): FunSpec.Builder
        fun addDeserialization(builder: FunSpec.Builder): FunSpec.Builder
        val identifier: String
    }

    private object JsonSerializationStatements : SerializationStatements {

        override fun addImport(builder: FileSpec.Builder): FileSpec.Builder {
            return builder
                .addImport("io.qalipsis.api.serialization", "JsonSerializers")
                .addImport("kotlinx.serialization", "encodeToString", "decodeFromString")
        }

        override fun addSerialization(builder: FunSpec.Builder): FunSpec.Builder {
            return builder.addStatement("return JsonSerializers.json.encodeToString(entity).encodeToByteArray()")
        }

        override fun addDeserialization(builder: FunSpec.Builder): FunSpec.Builder {
            return builder.addStatement("return JsonSerializers.json.decodeFromString(source.decodeToString())")
        }

        override val identifier: String = "kjson"
    }

    private object ProtobufSerializationStatements : SerializationStatements {

        override fun addImport(builder: FileSpec.Builder): FileSpec.Builder {
            return builder
                .addImport("io.qalipsis.api.serialization", "ProtobufSerializers")
                .addImport("kotlinx.serialization", "decodeFromByteArray", "encodeToByteArray")
        }

        override fun addSerialization(builder: FunSpec.Builder): FunSpec.Builder {
            return builder.addStatement("return ProtobufSerializers.protobuf.encodeToByteArray(entity)")
        }

        override fun addDeserialization(builder: FunSpec.Builder): FunSpec.Builder {
            return builder.addStatement("return ProtobufSerializers.protobuf.decodeFromByteArray(source)")
        }

        override val identifier: String = "kprotobuf"

    }
}
