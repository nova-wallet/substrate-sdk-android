package io.github.nova_wallet.substrate_sdk_android.codegen.types

import com.squareup.kotlinpoet.*
import io.github.nova_wallet.substrate_sdk_android.codegen.common.TypeFormatting.unknownType
import io.github.nova_wallet.substrate_sdk_android.codegen.common.TypeUnfolding
import io.github.nova_wallet.substrate_sdk_android.codegen.common.markSerializable
import io.github.nova_wallet.substrate_sdk_android.codegen.common.maybeMarkAsContextual
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Null
import java.io.File

class VariantCodegen(
    parentDirectory: File,
    configuration: Configuration,
    typeUnfolding: TypeUnfolding,
    private val structTypeCodegen: StructTypeCodegen,
) : TypeCodegen<DictEnum>(parentDirectory, configuration, typeUnfolding) {

    override fun FileSpec.Builder.applyType(type: DictEnum, path: TypePath) {
        val rootClassBuilder = TypeSpec.classBuilder(path.typeName)
            .addModifiers(KModifier.SEALED)
            .markSerializable()

        val rootClassName = ClassName(path.packageName, path.typeName)

        type.elements.values.forEach { entryValue ->
            val variantName = entryValue.name
            val variantType = entryValue.value.value ?: unknownType(type.name, variantName)

            val variantFieldTypeName = variantType.toTypeName(parentType = type.name)

            val childTypeSpecBuilder = when (variantType) {
                // object VariantName: Root()
                is Null -> {
                    TypeSpec.objectBuilder(variantName)
                        .superclass(rootClassName)
                }
                // VariantName(field1: TYPE1, field2: TYPE2,...): Root()
                is Struct -> {
                    val classBuilder = TypeSpec.classBuilder(variantName)
                        .superclass(rootClassName)

                    with(structTypeCodegen) {
                        classBuilder.applyStruct(variantType)
                    }

                    classBuilder
                }
                // VariantName(variantName: TYPE): Root()
                else -> {
                    val variantFieldName = variantName.toLowerCase()

                    TypeSpec.classBuilder(variantName)
                        .primaryConstructor(
                            FunSpec.constructorBuilder()
                                .addParameter(variantFieldName, variantFieldTypeName)
                                .build()
                        )
                        .addProperty(
                            PropertySpec.builder(variantFieldName, variantFieldTypeName)
                                .initializer(variantFieldName)
                                .maybeMarkAsContextual(configuration, variantType)
                                .build()
                        )
                        .superclass(rootClassName)
                }
            }

            val childTypeSpec = childTypeSpecBuilder
                .markSerializable()
                .build()

            rootClassBuilder.addType(childTypeSpec)
        }

        addType(rootClassBuilder.build())
    }
}
