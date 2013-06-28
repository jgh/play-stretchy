package controllers

import play.api.mvc._
import stretchy.ES
import org.elasticsearch.index.query.QueryBuilders
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.data._
import play.api.data.Forms._

object Application extends Controller {
  val thingForm = Form(
    tuple(
      "name" -> text,
      "description" -> text
    )
  )

  def index(q: Option[String]) = Action {
    val queryString = q.filterNot(_.trim.isEmpty)
      val searchResponse = ES.execute(client => {
        val query = queryString.map(f => QueryBuilders.queryString(f)).getOrElse(QueryBuilders.matchAllQuery())
        client.prepareSearch("stuff")
          .addFields("name",  "description")
          .setQuery(query)
          .setSize(20)
      })
      Async {
        searchResponse.map(results => {
          Ok(views.html.index(queryString, results))
        })
      }
  }
  def add = Action   {implicit request =>

    val form = thingForm.bindFromRequest()
    println(form)
    val (name, description) = form.get

    ES.execute(client => {

        val entry = Json.obj(
          "name" -> name,
          "description" -> description
        )

        println("indexing: " + name)
        client.prepareIndex()
          .setIndex("stuff")
          .setType("thingy")
          .setSource(Json.stringify(entry))
      })

    Redirect(routes.Application.index(None))
  }
}