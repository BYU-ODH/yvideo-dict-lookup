package controllers

import java.net.URLEncoder
import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.duration._
import play.api.libs.json._
import play.api.libs.ws.WS
import play.api.Play.{current, configuration}
import ExecutionContext.Implicits.global

object LookupSeaLang extends Translator {
  /**
   *  Russian Digital Reference Project Web API:
   *    http://www.sealang.net/russtest/api.htm 
   *
   * Example Requests
   *     EN-RU Dictionary: http://www.sealang.net/russtest/api.pl?service=dictionary&query=test&format=html&number=5&fold=yes&resource=l2l1
   *     RU-EN Dictionary: http://www.sealang.net/russtest/api.pl?service=dictionary&query=злой&format=html&number=5&fold=yes&resource=l1l2
   *     Russian Dictionary: http://www.sealang.net/russtest/api.pl?service=dictionary&query=злой&format=html&number=5&fold=yes&resource=all
   *     Russian Dictionary: http://www.sealang.net/russtest/api.pl?service=dictionary&query=злой&format=html&encode=unicode&number=5&fold=yes&return=syn&sort=down&rank=alpha&resource=all
   */

  val name = "SealLang"
  val expiration = Utils.getExpiration("seaLang")

  /* Grabs all the direct Translations */
  def grabTranslations(json:Seq[JsValue]) : Seq[String] = {
    (for { entry <- json } yield {
      val word = (entry\"$t").asOpt[String]
      word.getOrElse("").replaceAll("\\n", "<br/>").stripPrefix("<br/>").stripSuffix("<br/>")
    }).filterNot(w=>w=="")
  }

  /**
   * Tried to Implement English to Russian Translations, but the api returns a horrid response that is inconsistent
   * This function is here in case the api ever gets updated
   */
  def englishToRussian(src: String, dest: String, text: String) = {
      None
  }

 /**
  * Russian To English Translations
  * TODO: After evaluation by users, possibly remove if it is to cumbersome to view.
  *       Similar prblem to function englishToRussian(src: String, dest: String, text: String)
  */
  def russianToEnglish(src: String, dest: String, text: String) = {
    val query = WS.url("http://www.sealang.net/russtest/api.pl")
    .withQueryString("service" -> "dictionary", "query" -> text, "format" -> "json", "phrase" -> text,
                     "number" -> "5", "fold" -> "yes", "resource" -> "l1l2","encode" -> "unicode").get()
    val result = Await.result(query, Duration.Inf)
    val json = result.json.asOpt[JsObject]

    if(result.status != 200) None
    else {
      val entries = (json.get \ "return" \ "entry"\\"sense")

      if (entries.size == 0) None
      else {
        val definitions = grabTranslations(entries)
        if (definitions.size != 0) {
          val temp = for { defin <- definitions } yield {
            "<b>" + text + ":</b><br/>" + defin
          }
          // Limit the translation to five definitions so that it does not get overbearing
          Some(temp.take(5))
        }
        else
          None
      }
    }
  }

  /**
   * Specifically Handles Russian Definitions
   */
  def russianToRussian(src: String, dest: String, text: String) = {
    val query = WS.url("http://www.sealang.net/russtest/api.pl")
    .withQueryString("service" -> "dictionary", "query" -> text, "format" -> "json", "phrase" -> text,
                     "number" -> "5", "fold" -> "yes", "encode" -> "unicode").get()
    val result = Await.result(query, Duration.Inf)
    val json = result.json.asOpt[JsObject]

    if(result.status != 200) None
    else {
      val entries = (json.get \ "return" \ "entry" \ "sense" \\ "def")

      if (entries.size == 0) None
      else {
        val definitions = grabTranslations(entries)
        if (definitions.size != 0) {
          var num = 0
          val temp = for { defin <- definitions } yield {
            num = num + 1
            "<b>" + num + ".)</b> " + defin
          }
          // Limit the translation to five definitions so that it does not get overbearing
          Some(temp.take(5))
        }
        else
          None
      }
    }
  }

  /**
   * Endpoint for translating via SeaLang
   */
  def translate(src: String, dest: String, text: String) = {
    val langVal = Map( "en" -> 1, "ru" -> 2).withDefaultValue(0)

    if(langVal(src) + langVal(dest) < 3) None
    else if (langVal(src) == 1) {          // English to Russian Translation
      englishToRussian(src, dest, text)
    } else if (langVal(dest) == 1){        // Russian to English Translation
      russianToEnglish(src, dest, text)
    } else {                               // Russian To Russian
      russianToRussian(src, dest, text)
    }
  }
}