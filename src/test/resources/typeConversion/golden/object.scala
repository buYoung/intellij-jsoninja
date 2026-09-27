// Warning: Scala field 'zipCode' cannot retain JSON key 'zip-code' without a serializer mapping.

case class RootAddres(
    zipCode: String
)

case class Root(
    active: Boolean,
    address: RootAddres,
    id: Long,
    name: String,
    scores: List[Long]
)
