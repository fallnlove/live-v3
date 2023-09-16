@file:OptIn(ExperimentalSerializationApi::class)

package org.icpclive.generator.schema


import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.*
import dev.adamko.kxstsgen.KxsTsGenerator
import dev.adamko.kxstsgen.core.TsDeclaration
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.json.*
import java.io.File

fun PrimitiveKind.toJsonTypeName(): String = when (this) {
    PrimitiveKind.BOOLEAN -> "boolean"
    PrimitiveKind.BYTE -> "number"
    PrimitiveKind.CHAR -> "number"
    PrimitiveKind.DOUBLE -> "number"
    PrimitiveKind.FLOAT -> "number"
    PrimitiveKind.INT -> "number"
    PrimitiveKind.LONG -> "number"
    PrimitiveKind.SHORT -> "number"
    PrimitiveKind.STRING -> "string"
}

@OptIn(ExperimentalSerializationApi::class)
fun SerialDescriptor.toJsonSchemaType(
    processed: MutableSet<String>,
    definitions: MutableMap<String, JsonElement>,
    extras: Map<String, JsonElement> = emptyMap(),
    extraTypeProperty: String? = null,
    title: String? = null,
): JsonElement {
    if (kind is PrimitiveKind) {
        require(extraTypeProperty == null)
        return JsonObject(mapOf("type" to JsonPrimitive((kind as PrimitiveKind).toJsonTypeName())))
    }
    if (isInline) {
        return getElementDescriptor(0).toJsonSchemaType(processed, definitions, extras, extraTypeProperty)
    }
    // Before processing, check for recursion
    val paramNamesWithTypes = elementDescriptors.map { it.serialName }
    val name = serialName + if (paramNamesWithTypes.isEmpty()) "" else "<${paramNamesWithTypes.joinToString(",")}>"
    val id = title ?: name
    if (!processed.contains(id)) {
        processed.add(id)
        val data = when (kind) {
            PolymorphicKind.OPEN -> TODO("Open polymorphic types are not supported")
            SerialKind.CONTEXTUAL -> TODO("Contextual types are not supported")
            PolymorphicKind.SEALED -> {
                require(extraTypeProperty == null)
                val typeFieldName = getElementName(0)
                val contextualDescriptor = getElementDescriptor(1)
                mapOf(
                    "oneOf" to JsonArray(
                        contextualDescriptor.elementDescriptors
                            .sortedBy { it.serialName }
                            .map {
                                it.toJsonSchemaType(
                                    processed,
                                    definitions,
                                    title = it.serialName.split(".").last(),
                                    extraTypeProperty = typeFieldName
                                )
                            }
                    )
                )
            }

            is PrimitiveKind -> error("Already handled")

            SerialKind.ENUM -> {
                require(extraTypeProperty == null)
                mapOf("enum" to JsonArray(elementNames.map { JsonPrimitive(it) }))
            }

            StructureKind.CLASS -> {
                JsonObject(
                    mapOf(
                        "type" to JsonPrimitive("object"),
                        "properties" to JsonObject(
                            listOfNotNull(extraTypeProperty).associateWith {
                                JsonObject(
                                    mapOf(
                                        "const" to JsonPrimitive(serialName),
                                        "default" to JsonPrimitive(serialName),
                                    )
                                )
                            } +
                                    (0 until elementsCount).associate {
                                        getElementName(it) to
                                                getElementDescriptor(it).toJsonSchemaType(processed, definitions)
                                    }
                        ),
                        "additionalProperties" to JsonPrimitive(false),
                        "required" to JsonArray(
                            (listOfNotNull(extraTypeProperty) +
                                    (0 until elementsCount).filterNot { isElementOptional(it) }
                                        .map { getElementName(it) }).map { JsonPrimitive(it) }
                        )
                    )
                )
            }

            StructureKind.LIST -> {
                mapOf(
                    "type" to JsonPrimitive("array"),
                    "items" to getElementDescriptor(0).toJsonSchemaType(processed, definitions)
                )
            }

            StructureKind.MAP -> {
                val keysSerializer = getElementDescriptor(0)
                val valuesSerializer = getElementDescriptor(1)
                when (keysSerializer.kind) {
                    PrimitiveKind.STRING -> {
                        JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("object")
                            )
                        )
                    }

                    SerialKind.ENUM -> {
                        JsonObject(mapOf(
                            "type" to JsonPrimitive("object"),
                            "properties" to JsonObject(
                                (0 until keysSerializer.elementsCount).associate {
                                    keysSerializer.getElementName(it) to valuesSerializer.toJsonSchemaType(
                                        processed,
                                        definitions
                                    )
                                }
                            )
                        ))
                    }

                    else -> error("Unsupported map key: $keysSerializer")
                }
            }

            StructureKind.OBJECT -> TODO("Object types are not supported")
        }
        val content = (extras + data).toMutableMap()
        if (title != null) {
            content["title"] = JsonPrimitive(title)
        }
        definitions[id] = JsonObject(content)
    }
    return JsonObject(mapOf("\$ref" to JsonPrimitive("#/\$defs/$id")))
}

fun SerialDescriptor.toJsonSchema(title: String): JsonElement {
    val definitions = mutableMapOf<String, JsonElement>()
    val mainSchema = toJsonSchemaType(
        title = title,
        processed = mutableSetOf(),
        definitions = definitions,
    )
    return JsonObject(
        (mainSchema as JsonObject) +
                mapOf(
                    "\$defs" to JsonObject(definitions),
                )
    )
}


private val json = Json {
    prettyPrint = true
}

class JsonCommand : CliktCommand(name = "json") {
    private val className by option(help = "Class name for which schema should be generated")
    private val output by option("--output", "-o", help = "File to print output").required()
    private val title by option("--title", "-t", help = "Title inside schema file").required()

    override fun run() {
        val thing = serializer(Class.forName(className))
        val serializer = thing.descriptor
        val schema = json.encodeToString(serializer.toJsonSchema(title))
        File(output).printWriter().use {
            it.println(schema)
        }
    }
}

// This is a copy-pasted generating method to work around a bug.
fun KxsTsGenerator.patchedGenerate(vararg serializers: KSerializer<*>) : String {
        return serializers
            .toSet()
            .flatMap { serializer -> descriptorsExtractor(serializer) }
            .toSet()
            .flatMap { descriptor -> elementConverter(descriptor) }
            .toSet()
            .groupBy { element -> sourceCodeGenerator.groupElementsBy(element) }
            .mapValues { (_, elements) ->
                elements
                    .filterIsInstance<TsDeclaration>()
                    .map { element -> sourceCodeGenerator.generateDeclaration(element) }
                    .filter { it.isNotBlank() }
                    .toSet() // workaround for https://github.com/adamko-dev/kotlinx-serialization-typescript-generator/issues/110
                    .joinToString(config.declarationSeparator)
            }
            .values
            .joinToString(config.declarationSeparator)
}

class TSCommand : CliktCommand(name = "type-script") {
    private val className by option(help = "Class name for which schema should be generated").multiple()
    private val output by option("--output", "-o", help = "File to print output").required()

    override fun run() {
        val tsGenerator = KxsTsGenerator()
        val things = className.map { serializer(Class.forName(it)) }
        File(output).printWriter().use {
            it.println(tsGenerator.patchedGenerate(*things.toTypedArray()))
        }
    }
}


class MainCommand : CliktCommand() {
    override fun run() {}
}

fun main(args: Array<String>) {
    MainCommand().subcommands(JsonCommand(), TSCommand()).main(args)
}