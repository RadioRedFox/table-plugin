package entity

import table.annotations.Column
import table.annotations.Table

@Table
data class Customer(
    val id: Long,
    val name: String,
    val lastName: String,
    val URLValue: String
) {
    companion object TableInfo
}

@Table(allProperties = false)
data class Employee(
    val id: Long,
    @Column("first_name")
    val firstName: String,
    @Column
    val lastName: String,
    val age: Int
) {
    companion object TableInfo
}

@Table("explicit_people")
data class Manager(
    val managerId: Long,
    val displayName: String
) {
    companion object TableInfo
}

@Table
data class CustomerOrder(
    val orderId: Long
) {
    companion object TableInfo
}
