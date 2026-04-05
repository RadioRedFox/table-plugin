package sometests

import entity.AllColumns
import entity.Customer
import entity.TableName
import entity.columnName


fun main() {
    println(Customer.TableName)
    println(Customer::URLValue.columnName)
    println(Customer.AllColumns)
}
