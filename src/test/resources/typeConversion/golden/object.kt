data class RootAddres(
    val zipCode: String
)

data class Root(
    val active: Boolean,
    val address: RootAddres,
    val id: Long,
    val name: String,
    val scores: List<Long>
)
