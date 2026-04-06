package sometests

import entity.AllColumns
import entity.AllColumnsSql
import entity.Customer
import entity.TableName
import entity.columnName


fun main() {
    println(Customer.TableName)
    println(Customer::URLValue.columnName)
    println(Customer.AllColumns)
    println(Customer.AllColumnsSql( prefix = "c_"))
}
