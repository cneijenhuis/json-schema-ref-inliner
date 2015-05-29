import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Await}
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import scala.language.postfixOps

import com.ning.http.client.{Response, AsyncHttpClient}
import com.ning.http.util.Base64
import dispatch._

import org.json4s._
import org.json4s.native.JsonMethods._

object RefInliner extends App {
  val baseUrl = "http://localhost:8080/sphere/schemas/"
  //val schemaUrl = baseUrl + "categories.schema.json"
  val schemaUrl = baseUrl + "customers.schema.json"
  val h: Http = new Http(new AsyncHttpClient)

  val base = resp(schemaUrl)
  Await.result(base flatMap { r => inline(parse(r.getResponseBody)) } map { json => println(pretty(render(json))) }, 2 seconds)
  h.shutdown()

  def resp(schemaUrl: String) = h(url(schemaUrl).GET)

  def inline(json: JValue) = { 
    val raj = refsAndJsons(references(json))  
    raj map {
      rj => json.transform {
        case JObject(JField("$ref", JString(s: String)) :: List()) => rj.get(s).getOrElse(JObject(List(JField("$ref", JString(s)))))
    } }
  }

  def refsAndJsons(refs: Set[String]): Future[Map[String, JValue]] = {
    val refsWithResp = refs map { ref =>
      resp(baseUrl + ref) flatMap { r =>
        inline(removeSchema(parse(r.getResponseBody))) map { json: JValue => (ref, json) } 
    } }
    Future.sequence(refsWithResp) map(set => set.toMap)
  }

  def references(json: JValue) =
    json filterField {
      case JField("$ref", _) => true
      case _ => false
    } flatMap { 
      case JField(_, JString(s: String)) => List(s)
      case _ => List()
    } toSet

  def removeSchema(p: JValue) =
    p.removeField { f => f match {
      case JField("$schema", _) => true
      case _ => false
    } }
}
