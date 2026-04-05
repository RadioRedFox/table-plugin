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
            .map { property ->
                ColumnInfo(
                    propertyName = property.simpleName.asString(),
                    columnName = resolveColumnName(property)
                )
            }
            .toList()

        val dependencies = classDeclaration.containingFile?.let { Dependencies(false, it) } ?: Dependencies(false)
        val file = codeGenerator.createNewFile(
            dependencies = dependencies,
            packageName = packageName,
            fileName = "${className}Utils"
        )

        OutputStreamWriter(file, StandardCharsets.UTF_8).use { writer ->
            writer.write(buildGeneratedSource(packageName, className, tableConfig.tableName, columns))
        }
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

    private fun resolveColumnName(property: KSPropertyDeclaration): String {
        val columnAnnotation = property.annotations
            .firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == COLUMN_ANNOTATION }

        val explicitName = columnAnnotation
            ?.arguments
            ?.firstOrNull { it.name?.asString() == "value" }
            ?.value as? String

        return explicitName?.takeIf { it.isNotBlank() }
            ?: toSnakeCase(property.simpleName.asString())
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
        columns: List<ColumnInfo>
    ): String {
        val builder = StringBuilder()

        if (packageName.isNotBlank()) {
            builder.append("package ").append(packageName).append('\n').append('\n')
        }

        builder.append("val ").append(className).append(".TableInfo.TableName: String").append('\n')
            .append("    get() = \"").append(escapeString(tableName)).append('"').append('\n').append('\n')

        for (column in columns) {
            builder.append("val ").append(className).append(".TableInfo.")
                .append(column.propertyName).append(": String").append('\n')
                .append("    get() = \"").append(escapeString(column.columnName)).append('"').append('\n').append('\n')
        }

        builder.append("val ").append(className).append(".TableInfo.AllColumns: List<String>").append('\n')
            .append("    get() = ")

        if (columns.isEmpty()) {
            builder.append("listOf()").append('\n')
        } else {
            builder.append("listOf(").append('\n')
            for ((index, column) in columns.withIndex()) {
                builder.append("        ").append(column.propertyName)
                if (index < columns.lastIndex) {
                    builder.append(',')
                }
                builder.append('\n')
            }
            builder.append("    )").append('\n')
        }

        return builder.toString()
    }

    private fun escapeString(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }
}

private data class ColumnInfo(
    val propertyName: String,
    val columnName: String
)

private data class TableConfig(
    val tableName: String,
    val allProperties: Boolean
)
