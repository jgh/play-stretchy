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
import scala.concurrent.Future
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.common.Base64
import play.api.templates.Html
import org.elasticsearch.common.bytes.BytesArray
import org.elasticsearch.search.highlight.HighlightBuilder
import org.elasticsearch.common.xcontent.XContentType
import models.Article

object Application extends Controller {
  val articleForm = Form(
    mapping(
      "title" -> text,
      "author" -> text,
      "description" -> text,
      "content" -> text,
      "tags" -> text
    )(((title,  author, desc,  content, tags) => Article(title, author, desc,  content,  tags.split(',').map(_.trim()).toSet)))
     (a  =>  Some(a.title,  a.author,  a.description,  a.content, a.tags.mkString(",")))
  )

  val indexName = "stuff"

  def article(id: String) = Action {
    implicit request =>
      Async {
        ES.execute(_.prepareGet(indexName, "article", id).setFields("content", "title")).map(rs => {
          import stretchy.XContentWriteable._
          if (rs.isExists) {
            val field = rs.getField("content")
            if (field != null) {
              val  content =  Html(new String(Base64.decode(field.getValue.asInstanceOf[String])))
              Ok(views.html.article(rs.getField("title").getValue.asInstanceOf[String],content))
              render {
                case Accepts.Html() => {
                  val  content =  Html(new String(Base64.decode(field.getValue.asInstanceOf[String])))
                  Ok(views.html.article(rs.getField("title").getValue.asInstanceOf[String],content))
                }
                case Accepts.Json() => {
                  Ok(rs)
                }
              }
            } else {
              NotFound
            }
          } else {
            NotFound
          }
        })
      }
  }

  def search(q: Option[String]) = Action {
    implicit request =>
      import stretchy.XContentWriteable._

      val query = q.filterNot(_.trim.isEmpty)
      val results: Option[Future[SearchResponse]] = query.map(queryString => {
        ES.execute(client => {
          val query = QueryBuilders.queryString(queryString)
          val search = client.prepareSearch(indexName)
            .addFields("title", "description", "author", "tags")
            .setQuery(query)
            .setSize(20)
            .addHighlightedField("content", 100, 3)
            .addHighlightedField("title", 100, 3)
            .addHighlightedField("description", 100, 3)
            .setHighlighterTagsSchema("styled")
          search
        })
      })
      render {
        case Accepts.Html() =>
          results.map(rf =>
            Async {

              rf.map(r  => {
//                r.getHits.getAt(0).highlightFields().values().
                Ok(views.html.index(query, Some(r)))

              })
            }
          ).getOrElse(
            Ok(views.html.index(query, None))
          )
        case Accepts.Json() =>
          results.map(results => {
            Async {
              results.map(Ok(_))
            }
          }).getOrElse(
            Ok(Json.obj())
          )
      }

  }

  def addForm = Action {
    implicit request =>
      Ok(views.html.add(articleForm.fill(Article("test", "test", "test", "Some  long  content"))))
  }

  def add = Action {
    implicit request =>

      val form: Form[Article] = articleForm.bindFromRequest()
      //    val (name, description) = form.get

      form.bindFromRequest.fold(
        formWithErrors => // binding failure, you retrieve the form containing errors,
          BadRequest(views.html.add(formWithErrors)),
        value => {

          val idxResp = indexArticle(value)
          Async {
             idxResp.map(resp =>
               Redirect(routes.Application.search(None)).flashing(("message" -> "Added  '%s' thing".format(value.title)))

             )
          }
        })

  }


  def indexArticle(article:Article): Future[IndexResponse] = {
    ES.execute(client => {

      val entry = Json.obj(
        "title" -> article.title,
        "description" -> article.description,
        "content" ->  Base64.encodeBytes(article.content.getBytes),
        "author" -> article.author,
        "tags"  ->  article.tags
      )

      println("indexing: " + article.title)
      client.prepareIndex()
        .setIndex(indexName)
        .setType("article")
        .setSource(Json.stringify(entry))
    })
  }

  def fieldValues(field: String) = Action {

    import play.api.libs.json._
    import stretchy.XContentJson._

    val searchResponse = ES.execute(client => {
      val facet = FacetBuilders.termsFacet("field-stats")
        .field(field)
        .allTerms(true)
        .order(TermsFacet.ComparatorType.TERM)
        .global(true)

      client.prepareSearch(indexName)
        .setQuery(QueryBuilders.matchAllQuery())
        .setSize(0)
        .addFacet(facet)
    })
    Async {
      searchResponse.map(results => {
        val json = Json.toJson(results)
        val jsonTransformer = (__ \ 'facets \ "field-stats" \ "terms").json.pick

        json.transform(jsonTransformer).fold(
          valid = (result => Ok(result)),
          invalid = (e => BadRequest(e.toString))
        )
      })
    }
  }
}
