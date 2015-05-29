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

object RefInliner {
  def printUsage() = {
    println("Usage:")
    println("Print inlined json schema to console:")
    println("https://raw.githubusercontent.com/sphereio/sphere-api-reference/master/schemas/customers.schema.json")
    println("Save inlined json into a file:")
    println("https://raw.githubusercontent.com/sphereio/sphere-api-reference/master/schemas/customers.schema.json customers-inlined.schema.json")
    println("You may use relative paths and '.' to use the original filename:")
    println("https://raw.githubusercontent.com/sphereio/sphere-api-reference/master/schemas/customers.schema.json ../another/dir/.")
    println("You can also use a list of schema/file pairs")
  }

  def main(args: Array[String]): Unit = {
    if (args.size == 0 || (args.size > 2 && args.size % 2 == 1)) {
      printUsage()
    }
    else if (args.size == 1) {
      val inliner = new RefInliner(args(0))
      val result = Await.result(inliner.inlineRefs(), 5 seconds)
      println(pretty(render(result)))
    }
    else {
      val futures = args.toList.grouped(2).map { _ match {
        case u :: f :: List() => 
          val inliner = new RefInliner(u)
          inliner.inlineRefs() map { json => 
            pretty(render(json)).getBytes(StandardCharsets.UTF_8) 
          } map { str =>
            val fileName = if (f.endsWith(".")) f.slice(0, f.size - 1) + inliner.schema else f
            Files.write(Paths.get(fileName), str)
          }
        case _ => Future.failed(new IllegalArgumentException)
      } }
      Await.result(Future.sequence(futures), (futures.size + 5) seconds)
    }
  }
}

class RefInliner(urlOfSchema: String) {
  def splitBaseUrlAndSchema(str: String): (String, String) = {
    val i = str.lastIndexOf("/")
    if (i < 1) throw new IllegalArgumentException(s"Not a valid url: $str")
    str.splitAt(i + 1)
  }

  val (baseUrl, schema) = splitBaseUrlAndSchema(urlOfSchema)

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
