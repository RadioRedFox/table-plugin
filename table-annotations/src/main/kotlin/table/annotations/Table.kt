package table.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Table(
    val value: String = "",
    val allProperties: Boolean = true
)
