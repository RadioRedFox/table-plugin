package extension

import java.util.Locale
import kotlin.reflect.KProperty1

val <T1, T2> KProperty1<T1, T2>.columnName: String
    get() = toSnakeCase(name)


private fun toSnakeCase(name: String): String {
    return name
        .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
        .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
        .lowercase(Locale.getDefault())
}