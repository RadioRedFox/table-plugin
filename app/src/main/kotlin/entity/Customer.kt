package entity

data class Customer(
    val id: Long,
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