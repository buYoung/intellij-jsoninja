trait Base { val id: Long }
case class Root(name: String, items: List[Item], maybe: Option[String]) extends Base {
  val id: Long = 1L
  private val secret: String = "hidden"
  def computed: Int = 2
}
case class Item(`type`: String)
object Types { type Users = Vector[Root]; val unrelated: String = "not a field" }
enum Status:
  case Ready, Done
