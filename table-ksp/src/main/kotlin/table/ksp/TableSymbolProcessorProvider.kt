package table.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.validate
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Locale

private const val TABLE_ANNOTATION = "table.annotations.Table"
private const val COLUMN_ANNOTATION = "table.annotations.Column"
private const val TABLE_INFO_NAME = "TableInfo"

class TableSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return TableSymbolProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger
        )
    }
}

private class TableSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(TABLE_ANNOTATION).toList()
        val deferred = mutableListOf<KSAnnotated>()

        for (symbol in symbols) {
            if (!symbol.validate()) {
                deferred += symbol
                continue
            }

            if (symbol !is KSClassDeclaration) {
                logger.warn("@Table supports classes only.", symbol)
                continue
            }

            generateForClass(symbol)
        }

        return deferred
    }

    private fun generateForClass(classDeclaration: KSClassDeclaration) {
        if (classDeclaration.classKind != ClassKind.CLASS) {
            logger.warn("@Table supports data classes only.", classDeclaration)
            return
        }
        if (!classDeclaration.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.DATA)) {
            logger.warn("@Table supports data classes only.", classDeclaration)
            return
        }
        if (!hasTableInfoCompanion(classDeclaration)) {
            logger.warn(
                "Class ${classDeclaration.simpleName.asString()} is marked with @Table but has no companion object TableInfo. Generation skipped.",
                classDeclaration
            )
            return
        }

        val className = classDeclaration.simpleName.asString()
        val packageName = classDeclaration.packageName.asString()
        val tableConfig = resolveTableConfig(classDeclaration, className)
        val properties = selectProperties(classDeclaration, tableConfig.allProperties)
        val columns = properties
            .map { property -> toColumnInfo(property) }
            .toList()
        val constructorParameterNames = classDeclaration.primaryConstructor
            ?.parameters
            ?.mapNotNull { it.name?.asString() }
            ?.toSet()
            ?: emptySet()
        val constructorProperties = classDeclaration.getAllProperties()
            .filter { it.simpleName.asString() in constructorParameterNames }
            .toList()
        val mapperColumns = constructorProperties
            .map { property -> toColumnInfo(property) }
            .toList()
        val canGenerateMapper = tableConfig.allProperties || constructorProperties.all { hasColumnAnnotation(it) }

        val dependencies = classDeclaration.containingFile?.let { Dependencies(false, it) } ?: Dependencies(false)
        val file = codeGenerator.createNewFile(
            dependencies = dependencies,
            packageName = packageName,
            fileName = "${className}Utils"
        )

        OutputStreamWriter(file, StandardCharsets.UTF_8).use { writer ->
            writer.write(
                buildGeneratedSource(
                    packageName = packageName,
                    className = className,
                    tableName = tableConfig.tableName,
                    columns = columns,
                    mapperColumns = mapperColumns,
                    generateMapper = canGenerateMapper
                )
            )
        }
    }

    private fun toColumnInfo(property: KSPropertyDeclaration): ColumnInfo {
        val resolvedType = property.type.resolve()
        val typeDeclaration = resolvedType.declaration
        val resolvedTypeName = typeDeclaration.qualifiedName?.asString()
            ?: typeDeclaration.simpleName.asString()
        val explicitColumnName = resolveExplicitColumnName(property)
        return ColumnInfo(
            propertyName = property.simpleName.asString(),
            columnName = explicitColumnName?.takeIf { it.isNotBlank() }
                ?: toSnakeCase(property.simpleName.asString()),
            typeName = resolvedTypeName,
            isNullable = resolvedType.nullability == com.google.devtools.ksp.symbol.Nullability.NULLABLE,
            isEnum = typeDeclaration is KSClassDeclaration && typeDeclaration.classKind == ClassKind.ENUM_CLASS,
            enumTypeName = resolvedTypeName,
            hasExplicitColumnName = !explicitColumnName.isNullOrBlank()
        )
    }

    private fun hasTableInfoCompanion(classDeclaration: KSClassDeclaration): Boolean {
        return classDeclaration.declarations
            .filterIsInstance<KSClassDeclaration>()
            .any { it.isCompanionObject && it.simpleName.asString() == TABLE_INFO_NAME }
    }

    private fun resolveTableConfig(classDeclaration: KSClassDeclaration, className: String): TableConfig {
        val tableAnnotation = classDeclaration.annotations
            .firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == TABLE_ANNOTATION }

        val explicitName = tableAnnotation
            ?.arguments
            ?.firstOrNull { it.name?.asString() == "value" }
            ?.value as? String

        val allProperties = tableAnnotation
            ?.arguments
            ?.firstOrNull { it.name?.asString() == "allProperties" }
            ?.value as? Boolean ?: true

        return TableConfig(
            tableName = explicitName?.takeIf { it.isNotBlank() } ?: toSnakeCase(className),
            allProperties = allProperties
        )
    }

    private fun selectProperties(
        classDeclaration: KSClassDeclaration,
        allProperties: Boolean
    ): Sequence<KSPropertyDeclaration> {
        val properties = classDeclaration.getAllProperties()
        return if (allProperties) {
            properties
        } else {
            properties.filter { hasColumnAnnotation(it) }
        }
    }

    private fun hasColumnAnnotation(property: KSPropertyDeclaration): Boolean {
        return property.annotations.any {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == COLUMN_ANNOTATION
        }
    }

    private fun resolveExplicitColumnName(property: KSPropertyDeclaration): String? {
        val columnAnnotation = property.annotations
            .firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == COLUMN_ANNOTATION }

        return columnAnnotation
            ?.arguments
            ?.firstOrNull { it.name?.asString() == "value" }
            ?.value as? String
    }

    private fun toSnakeCase(name: String): String {
        return name
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
            .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .lowercase(Locale.getDefault())
    }

    private fun buildGeneratedSource(
        packageName: String,
        className: String,
        tableName: String,
        columns: List<ColumnInfo>,
        mapperColumns: List<ColumnInfo>,
        generateMapper: Boolean
    ): String {
        val builder = StringBuilder()

        if (packageName.isNotBlank()) {
            builder.append("package ").append(packageName).append('\n').append('\n')
        }
        builder.append("import kotlin.reflect.KProperty1").append('\n')
        builder.append("import java.util.Locale").append('\n')
        appendResultSetMapperImports(builder, generateMapper)

        builder.append("val ").append(className).append(".TableInfo.TableName: String").append('\n')
            .append("    get() = \"").append(escapeString(tableName)).append('"').append('\n').append('\n')

        val explicitColumns = columns.filter { it.hasExplicitColumnName }
        builder.append("val <T> KProperty1<").append(className).append(", T>.columnName: String").append('\n')
        if (explicitColumns.isEmpty()) {
            builder.append("    get() = ")
                .append(className.replaceFirstChar { it.lowercase(Locale.getDefault()) })
                .append("ToSnakeCase(name)").append('\n').append('\n')
        } else {
            builder.append("    get() = when (this) {").append('\n')
            for (column in explicitColumns) {
                builder.append("        ").append(className).append("::").append(column.propertyName)
                    .append(" -> \"").append(escapeString(column.columnName)).append('"').append('\n')
            }
            builder.append("        else -> ").append(className.replaceFirstChar { it.lowercase(Locale.getDefault()) })
                .append("ToSnakeCase(name)").append('\n')
                .append("    }").append('\n').append('\n')
        }

        builder.append("val ").append(className).append(".TableInfo.AllColumns: List<String>").append('\n')
            .append("    get() = ")

        if (columns.isEmpty()) {
            builder.append("listOf()").append('\n')
        } else {
            builder.append("listOf(").append('\n')
            for ((index, column) in columns.withIndex()) {
                builder.append("        ").append(className).append("::").append(column.propertyName).append(".columnName")
                if (index < columns.lastIndex) {
                    builder.append(',')
                }
                builder.append('\n')
            }
            builder.append("    )").append('\n')
        }

        builder.append('\n')
            .append("private fun ").append(className.replaceFirstChar { it.lowercase(Locale.getDefault()) })
            .append("ToSnakeCase(name: String): String {").append('\n')
            .append("    return name").append('\n')
            .append("        .replace(Regex(\"([A-Z]+)([A-Z][a-z])\"), \"\$1_\$2\")").append('\n')
            .append("        .replace(Regex(\"([a-z0-9])([A-Z])\"), \"\$1_\$2\")").append('\n')
            .append("        .lowercase(Locale.getDefault())").append('\n')
            .append("}").append('\n')

        appendResultSetMapperFunction(
            builder = builder,
            className = className,
            columns = columns,
            mapperColumns = mapperColumns,
            generateMapper = generateMapper
        )

        return builder.toString()
    }

    private fun escapeString(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }
}

internal data class ColumnInfo(
    val propertyName: String,
    val columnName: String,
    val typeName: String,
    val isNullable: Boolean,
    val isEnum: Boolean,
    val enumTypeName: String,
    val hasExplicitColumnName: Boolean
)

private data class TableConfig(
    val tableName: String,
    val allProperties: Boolean
)
