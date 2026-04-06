package entity

import enums.CustomerStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import table.annotations.Column
import table.annotations.Table

@Table
data class Customer(
    val id: Long,
    val name: String,
    @Column("pizdec")
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
    val managerId: Long?,
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

@Table
data class AuditEvent(
    val id: UUID,
    val occurredOn: LocalDate,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val processedAt: LocalDateTime?,
    val archivedOn: LocalDate?,
    var pet: Int,
    var pet2: Int?,
) {
    companion object TableInfo
}

@Table
data class CustomerStateLog(
    val id: Long,
    val status: CustomerStatus,
    val previousStatus: CustomerStatus?
) {
    companion object TableInfo
}

@Table
data class Guest(
    val id: Long,
    val displayName: String
)
