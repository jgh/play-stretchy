package controllers

import play.api.mvc._
import stretchy.ES
import org.elasticsearch.index.query.QueryBuilders
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.data._
import play.api.data.Forms._
import org.elasticsearch.search.facet.FacetBuilders
import org.elasticsearch.search.facet.terms.TermsFacet

object Application extends Controller {
  val thingForm = Form(
    tuple(
      "name" -> text,
      "description" -> text
    )
  )

  val indexName = "stuff"

  def index(q: Option[String]) = Action  { implicit  request  =>
    q.filterNot(_.trim.isEmpty).map(queryString  =>  {
      val searchResponse = ES.execute(client => {
        val query = QueryBuilders.queryString(queryString)
        client.prepareSearch(indexName)
          .addFields("name",  "description")
          .setQuery(query)
          .setSize(20)
      })
      Async {
        searchResponse.map(results => {
          Ok(views.html.index(q, results ))
        })
      }

    }).getOrElse(
      Ok(views.html.index(q, null ))
    )
  }

  def addForm = Action   {implicit request =>
     Ok(views.html.add(thingForm.fill(("test", "test"))))
  }

  def add = Action   {implicit request =>

    val form: Form[(String, String)] = thingForm.bindFromRequest()
    println(form)
//    val (name, description) = form.get

    form.bindFromRequest.fold(
      formWithErrors => // binding failure, you retrieve the form containing errors,
        BadRequest(views.html.add(formWithErrors)),
      value =>  {

        val (name, description) = value
        ES.execute(client => {

          val entry = Json.obj(
            "name" -> name,
            "description" -> description
          )

          println("indexing: " + name)
          client.prepareIndex()
            .setIndex(indexName)
            .setType("thingy")
            .setSource(Json.stringify(entry))
        })
        println("Added")
        Redirect(routes.Application.index(None)).flashing(("message" ->  "Added  '%s' thing".format(name)))
      })

      }

  def fieldValues(field:String) = Action {
    import play.api.libs.json._
    import stretchy.XContentJson._

      val  searchResponse =  ES.execute(client => {
        val facet  = FacetBuilders.termsFacet("field-stats")
          .field(field)
          .allTerms(true)
          .order(TermsFacet.ComparatorType.TERM)
          .global(true)

        client.prepareSearch(indexName)
          .setQuery( QueryBuilders.matchAllQuery())
          .setSize(0)
          .addFacet(facet)
      })
      Async {
        searchResponse.map(results => {
          val json =  Json.toJson(results)
          val jsonTransformer = (__ \ 'facets \ "field-stats" \ "terms" ).json.pick

          json.transform(jsonTransformer).fold(
            valid = ( result => Ok(result) ),
            invalid = ( e => BadRequest(e.toString) )
          )
        })
      }
  }
  }
