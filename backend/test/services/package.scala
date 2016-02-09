import scala.io.Source

package object services {
  def asString(resource: String) = Source.fromInputStream(getOnClasspath(resource)).mkString
  private def getOnClasspath(resource: String) = getClass.getResourceAsStream(s"/$resource")
}
