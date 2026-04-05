package entity

import java.sql.ResultSet
import java.util.Locale
import kotlin.reflect.KProperty1

data class Customer(
    val someId: Long?,
//    @Column("name_column")
    val name: String
) {
    companion object TableInfo
}

val Customer.TableName: String
    get() = "customer"

val Customer.TableInfo.id: String
    get() = "id"

val Customer.TableInfo.name: String
    get() = "name"

val Customer.TableInfo.AllColumns: List<String>
    get() = listOf(
        id,
        name
    )

fun ResultSet.toCustomer(): Customer {
    return Customer(
        someId = getObject(Customer.TableInfo.name, Long::class.javaObjectType)!!,
        name = getString(Customer.TableInfo.name)!!
    )
}

val <T> KProperty1<Customer, T>.columnName: String
    get() {
        return when(this) {
            Customer::name -> "name_column"
            else -> toSnakeCase(name)
        }
    }


fun toSnakeCase(name: String): String {
    return name
        .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
        .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
        .lowercase(Locale.getDefault())
}

fun main() {
    println(Customer::someId.columnName)
}
