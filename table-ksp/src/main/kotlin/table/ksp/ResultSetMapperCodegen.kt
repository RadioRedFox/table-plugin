package table.ksp

internal fun appendResultSetMapperImports(
    builder: StringBuilder,
    generateMapper: Boolean
) {
    if (generateMapper) {
        builder.append("import java.sql.ResultSet").append('\n').append('\n')
    }
}

internal fun appendResultSetMapperFunction(
    builder: StringBuilder,
    className: String,
    columns: List<ColumnInfo>,
    mapperColumns: List<ColumnInfo>,
    generateMapper: Boolean
) {
    if (!generateMapper) {
        return
    }

    builder.append('\n')
    val generatedColumnPropertyNames = columns.map { it.propertyName }.toSet()
    builder.append("fun ResultSet.to").append(className).append("(): ").append(className).append(" {").append('\n')
        .append("    return ").append(className).append("(").append('\n')

    for ((index, column) in mapperColumns.withIndex()) {
        builder.append("        ").append(column.propertyName).append(" = ")
            .append(
                buildReadExpression(
                    className = className,
                    column = column,
                    useGeneratedColumnRef = generatedColumnPropertyNames.contains(column.propertyName)
                )
            )
        if (index < mapperColumns.lastIndex) {
            builder.append(',')
        }
        builder.append('\n')
    }

    builder.append("    )").append('\n')
        .append("}").append('\n')
}

private fun buildReadExpression(
    className: String,
    column: ColumnInfo,
    useGeneratedColumnRef: Boolean
): String {
    val columnRef = if (useGeneratedColumnRef) {
        "$className::${column.propertyName}.columnName"
    } else {
        "\"${escapeMapperString(column.columnName)}\""
    }
    val primitiveGetter = if (!column.isNullable) {
        primitiveGetterName(column.typeName)
    } else {
        null
    }
    val baseExpression = when {
        column.isEnum && column.isNullable ->
            "getString($columnRef)?.let { enumValueOf<${column.enumTypeName}>(it) }"
        column.isEnum ->
            "enumValueOf<${column.enumTypeName}>(getString($columnRef)!!)"
        primitiveGetter != null -> "$primitiveGetter($columnRef)"
        column.typeName == "kotlin.String" || column.typeName == "String" -> "getString($columnRef)"
        else -> "getObject($columnRef, ${buildJavaObjectType(column.typeName)}::class.javaObjectType)"
    }
    val shouldForceNotNull = !column.isNullable && primitiveGetter == null && !column.isEnum

    return if (!shouldForceNotNull) {
        baseExpression
    } else {
        "$baseExpression!!"
    }
}

private fun primitiveGetterName(typeName: String): String? {
    return when (typeName) {
        "kotlin.Int", "Int" -> "getInt"
        "kotlin.Long", "Long" -> "getLong"
        "kotlin.Short", "Short" -> "getShort"
        "kotlin.Byte", "Byte" -> "getByte"
        "kotlin.Double", "Double" -> "getDouble"
        "kotlin.Float", "Float" -> "getFloat"
        "kotlin.Boolean", "Boolean" -> "getBoolean"
        else -> null
    }
}

private fun buildJavaObjectType(typeName: String): String {
    return when (typeName) {
        "kotlin.Int" -> "Int"
        "kotlin.Long" -> "Long"
        "kotlin.Short" -> "Short"
        "kotlin.Byte" -> "Byte"
        "kotlin.Double" -> "Double"
        "kotlin.Float" -> "Float"
        "kotlin.Boolean" -> "Boolean"
        "kotlin.Char" -> "Char"
        "kotlin.String" -> "String"
        else -> typeName
    }
}

private fun escapeMapperString(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}
