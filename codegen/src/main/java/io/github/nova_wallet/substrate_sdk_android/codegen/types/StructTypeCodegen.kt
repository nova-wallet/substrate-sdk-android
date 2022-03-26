package io.github.nova_wallet.substrate_sdk_android.codegen.types

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import java.io.File

class StructTypeCodegen(
    parentDirectory: File,
    configuration: Configuration,
) : TypeCodegen<Struct>(parentDirectory, configuration) {

    override fun FileSpec.Builder.applyType(type: Struct, path: TypePath) {
        val typeSpec = TypeSpec.classBuilder(path.typeName)
            .apply { applyStruct(type) }
            .build()

        addType(typeSpec)
    }

    fun TypeSpec.Builder.applyStruct(struct: Struct) {
        val constructorSpecBuilder = FunSpec.constructorBuilder()

        struct.mapping.forEach { (fieldName, fieldTypeReference) ->
            val fieldType = fieldTypeReference.value ?: unknownType(struct.name, fieldName)
            val fieldClassName = fieldType.toTypeName(struct.name joinTypeName fieldName)

            addProperty(
                PropertySpec.builder(fieldName, fieldClassName)
                    .initializer(fieldName)
                    .build()
            )

            constructorSpecBuilder.addParameter(fieldName, fieldClassName)
        }

        primaryConstructor(constructorSpecBuilder.build())
    }
}
