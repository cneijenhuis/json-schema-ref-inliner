import java.nio.charset.StandardCharsets
import java.nio.file.{Paths, Files}

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

  def splitBaseUrlAndSchema(str: String): (String, String) = {
    val i = str.lastIndexOf("/")
    if (i < 1) throw new IllegalArgumentException("Not a valid url")
    str.splitAt(i + 1)
  }

  override def main(args: Array[String]): Unit = {
    val (base, schema) = if (args.size > 0) splitBaseUrlAndSchema(args(0)) else (baseUrl, schemaUrl)
    val inliner = new RefInliner(base, schema)
    val result = Await.result(inliner.inlineRefs(), 5 seconds)

    if (args.size > 1) {
      Files.write(Paths.get(args(1)), pretty(render(result)).getBytes(StandardCharsets.UTF_8))
    } else println(pretty(render(result)))
  }
}

class RefInliner(baseUrl: String, schema: String) {
  lazy val h: Http = new Http(new AsyncHttpClient)

  def inlineRefs() = {
    val orig = resp(baseUrl + schema)
    val result = orig flatMap { r => inline(parse(r.getResponseBody)) }
    result map { w => h.shutdown() }
    result
  }

  private def resp(schemaUrl: String) = h(url(schemaUrl).GET)

  private def inline(json: JValue) = { 
    val raj = refsAndJsons(references(json))  
    raj map {
      rj => json.transform {
        case JObject(JField("$ref", JString(s: String)) :: List()) => rj.get(s).getOrElse(JObject(List(JField("$ref", JString(s)))))
    } }
  }

  private def refsAndJsons(refs: Set[String]): Future[Map[String, JValue]] = {
    val refsWithResp = refs map { ref =>
      resp(baseUrl + ref) flatMap { r =>
        inline(removeSchema(parse(r.getResponseBody))) map { json: JValue => (ref, json) } 
    } }
    Future.sequence(refsWithResp) map(set => set.toMap)
  }

  private def references(json: JValue) =
    json filterField {
      case JField("$ref", _) => true
      case _ => false
    } flatMap { 
      case JField(_, JString(s: String)) => List(s)
      case _ => List()
    } toSet

  private def removeSchema(p: JValue) =
    p.removeField { f => f match {
      case JField("$schema", _) => true
      case _ => false
    } }
}
