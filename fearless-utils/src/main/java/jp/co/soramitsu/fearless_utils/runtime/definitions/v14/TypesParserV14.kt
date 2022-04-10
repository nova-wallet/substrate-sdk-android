package jp.co.soramitsu.fearless_utils.runtime.definitions.v14

import jp.co.soramitsu.fearless_utils.extensions.snakeCaseToCamelCase
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypePreset
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypePresetBuilder
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.alias
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.getOrCreate
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.newBuilder
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Alias
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.FixedArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Tuple
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Vec
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Bytes
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Null
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.Compact
import jp.co.soramitsu.fearless_utils.runtime.definitions.v14.typeMapping.SiTypeMapping
import jp.co.soramitsu.fearless_utils.runtime.definitions.v14.typeMapping.default
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.LookupSchema
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.PortableType
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.RegistryType
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.TypeDefArray
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.TypeDefBitSequence
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.TypeDefCompact
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.TypeDefComposite
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.TypeDefCompositeField
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.TypeDefEnum
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.TypeDefSequence
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.TypeDefVariant
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.TypeDefVariantItem
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct

@OptIn(ExperimentalUnsignedTypes::class)
object TypesParserV14 {

    private class Params(
        val types: List<EncodableStruct<PortableType>>,
        val typeMapping: SiTypeMapping,
        val uniquePathNames: Set<String>,
        val typesBuilder: TypePresetBuilder
    )

    fun parse(
        lookup: EncodableStruct<LookupSchema>,
        typePreset: TypePreset,
        typeMapping: SiTypeMapping = SiTypeMapping.default()
    ): TypePreset {
        val builder = typePreset.newBuilder()
        val rawTypes = lookup[LookupSchema.types]

        val uniquePathNames = findUniquePathNames(rawTypes)

        val params = Params(rawTypes, typeMapping, uniquePathNames, builder)

        parseParams(params)

        return params.typesBuilder
    }

    private fun findUniquePathNames(types: List<EncodableStruct<PortableType>>): Set<String> {
        return types
            .groupingBy { it.pathBasedName(appendId = false) }
            .eachCount()
            .mapNotNullTo(mutableSetOf()) { (name, count) -> name.takeIf { count == 1 } }
    }

    private fun parseParams(params: Params) {
        // first stage - parsing according to standard rules
        params.types.forEach { type ->
            val constructedType = parseParam(params, type) ?: return@forEach
            params.typesBuilder.type(constructedType)

            val aliasedName = type[PortableType.id].toString()

            if (aliasedName != constructedType.name) {
                params.typesBuilder.alias(alias = aliasedName, original = constructedType.name)
            }
        }

        // second stage - overwrite from SiTypeMapping
        // this is done after full standard resolution so SiTypeMapping's
        // would have access to lookahead types
        params.types.forEach { type ->
            val name = type.name(params)
            val fromTypeMapping = params.typeMapping.map(type, name, params.typesBuilder)

            fromTypeMapping?.let { params.typesBuilder.type(it) }
        }
    }

    private fun EncodableStruct<PortableType>.name(
        parsingParams: Params
    ): String {
        return pathNameResolvingNotUnique(whitelist = parsingParams.uniquePathNames)
            ?: get(PortableType.id).toString()
    }

    private fun parseParam(params: Params, portableType: EncodableStruct<PortableType>): Type<*>? {
        val typesBuilder = params.typesBuilder

        val name = portableType.name(params)
        val type = portableType[PortableType.type]

        return when (val def = type[RegistryType.def]) {
            is EncodableStruct<*> -> {
                when (def.schema) {
                    is TypeDefComposite -> {
                        val list = def[TypeDefComposite.fields2]
                        val childrenTypeMapping =
                            parseTypeMapping(params, list, useSnakeCaseForFieldNames = true)

                        typeMappingToType(name, childrenTypeMapping)
                    }

                    is TypeDefArray -> {
                        FixedArray(
                            name,
                            def[TypeDefArray.len].toInt(),
                            params.typesBuilder.getOrCreate(def[TypeDefArray.type].toString())
                        )
                    }

                    is TypeDefSequence -> {
                        Vec(
                            name,
                            params.typesBuilder.getOrCreate(def[TypeDefSequence.type].toString())
                        )
                    }

                    is TypeDefVariant -> {
                        val variants = def[TypeDefVariant.variants]

                        val transformedVariants = variants.associateBy(
                            keySelector = { it[TypeDefVariantItem.index].toInt() },
                            valueTransform = {
                                val fields = it[TypeDefVariantItem.fields2]

                                val itemName = it[TypeDefVariantItem.index].toString()

                                val children = parseTypeMapping(
                                    params = params,
                                    childrenRaw = fields,
                                    useSnakeCaseForFieldNames = false
                                )

                                val valueReference = when (children.size) {
                                    0 -> TypeReference(Null)
                                    1 -> {
                                        when (children) {
                                            is FieldsTypeMapping.Named -> {
                                                TypeReference(Struct(itemName, children.value))
                                            }
                                            // unwrap single unnamed struct
                                            is FieldsTypeMapping.Unnamed -> children.value.first()
                                        }
                                    }
                                    else -> {
                                        val fieldsType = typeMappingToType(itemName, children)

                                        TypeReference(fieldsType)
                                    }
                                }

                                DictEnum.Entry(
                                    name = it[TypeDefVariantItem.name],
                                    value = valueReference
                                )
                            }
                        )

                        DictEnum(name, transformedVariants)
                    }

                    is TypeDefCompact -> Compact(name)

                    is TypeDefBitSequence -> {
                        Tuple(
                            name = name,
                            typeReferences = listOf(
                                def[TypeDefBitSequence.bit_store_type],
                                def[TypeDefBitSequence.bit_order_type]
                            ).map { params.typesBuilder.getOrCreate(it.toString()) }
                        )
                    }
                    else -> null
                }
            }
            is TypeDefEnum -> {
                when (def) {
                    TypeDefEnum.str -> {
                        Alias(name, TypeReference(Bytes))
                    }
                    TypeDefEnum.char -> {
                        Alias(name, TypeReference(Bytes))
                    }
                    else -> {
                        Alias(name, typesBuilder.getOrCreate(def.localName))
                    }
                }
            }
            is List<*> -> {
                val typeReferences = def.map { params.typesBuilder.getOrCreate(it.toString()) }

                Tuple(name = name, typeReferences = typeReferences)
            }
            else -> {
                null
            }
        }
    }

    private fun typeMappingToType(itemName: String, typeMapping: FieldsTypeMapping): Type<*> {
        return when (typeMapping) {
            is FieldsTypeMapping.Named -> Struct(itemName, typeMapping.value)
            is FieldsTypeMapping.Unnamed -> Tuple(itemName, typeMapping.value)
        }
    }

    private fun parseTypeMapping(
        params: Params,
        childrenRaw: List<EncodableStruct<TypeDefCompositeField>>,
        useSnakeCaseForFieldNames: Boolean
    ): FieldsTypeMapping {
        val children = childrenRaw.map { child ->
            val typeIndex = child[TypeDefCompositeField.type].toString()
            val entryName = child[TypeDefCompositeField.name]?.let {
                if (useSnakeCaseForFieldNames) it.snakeCaseToCamelCase() else it
            }

            entryName to params.typesBuilder.getOrCreate(typeIndex)
        }

        // there should either be all named arguments or all unnamed
        val allFieldsHasNames = children.all { (name, _) -> name != null }

        return if (allFieldsHasNames) {
            val childrenAsMap = children.associateByTo(
                LinkedHashMap(),
                keySelector = { (name, _) -> name!! },
                valueTransform = { (_, typeReference) -> typeReference }
            )

            FieldsTypeMapping.Named(childrenAsMap)
        } else {
            val childrenAsList = children.map { (_, typeRef) -> typeRef }

            FieldsTypeMapping.Unnamed(childrenAsList)
        }
    }

    private sealed class FieldsTypeMapping {

        abstract val size: Int

        class Named(val value: LinkedHashMap<String, TypeReference>) : FieldsTypeMapping() {
            override val size: Int = value.size
        }

        class Unnamed(val value: List<TypeReference>) : FieldsTypeMapping() {
            override val size: Int = value.size
        }
    }

    private fun EncodableStruct<PortableType>.pathBasedName(appendId: Boolean): String? {
        val pathSegments = this[PortableType.type][RegistryType.path]

        return if (pathSegments.isEmpty()) {
            null
        } else {
            val path = pathSegments.joinToString(separator = ".")
            val suffix = if (appendId) this[PortableType.id] else ""

            path + suffix
        }
    }

    private fun EncodableStruct<PortableType>.pathNameResolvingNotUnique(
        whitelist: Set<String>
    ): String? {
        return when (val withoutTypeParameters = pathBasedName(appendId = false)) {
            null -> null
            in whitelist -> withoutTypeParameters
            else -> pathBasedName(appendId = true)
        }
    }
}
