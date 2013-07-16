# Playful ElasticSearch

This is a plugin for Play 2.1, enabling support for [ElasticSearch](http://www.elasticsearch.org)

Elasticsearch already has an extensive java api. This plugin does not attempt to replicate the existing API its
goal is simply to make the API easier to use in a Play environment.

## Main features

 * Configure ES client connection in Play config
 * Configure index mappings in Play config
 * Bridge ES and play futures to allow the use of Async for ES results


## Getting Started

### Add stretchy to your dependencies

#### In project/Build.scala

```
 val appDependencies = Seq(
  "play-stretchy" %% "play-stretchy" % "0.0.1"
 )

 val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
  resolvers += Resolver.url("jgh GitHub Repository", url("http://jgh.github.com/releases/"))(Resolver.ivyStylePatterns)
 )
```


### Configure your application to use plugin

#### add to your conf/play.plugins

```
500:stretchy.ESPlugin
```

### Configure your elasticsearch access within `application.conf`

This plugin reads properties from the `application.conf` and gives you easy access to elasticsearch.

#### add this to your conf/application.conf

```
elasticsearch = {
 client {
  node: {
   local:true
   data:true
  }
 }
 indicies: [
 {
  name: stuff
  deleteIndex: true
  createIndex: true
 }
 ]
}
```
This will create a new local node that holds data. It will also drop and recreate a 'stuff' index on startup/reload.

This is all you need to start indexing and searching stuff. Elasticsearch will automatically create mappings for the objects you index based on the
 first document this is indexed. To define these mappings yourself see [Index Mappings](#Index-Mappings). By default to index will be stored in the 'data' subdirectory of the project directory.

See [Client Configuration](#Client-Configuration) for how to connect to existing elasticsearch nodes

### Play2 controller sample

```
package controllers

import play.api.mvc._
import stretchy.ES
import org.elasticsearch.index.query.QueryBuilders
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._

object Application extends Controller {

 def index(q: Option[String]) = Action {
 val queryString = q.filterNot(_.trim.isEmpty)
  val searchResponse = ES.execute(client => {
  val query = queryString.map(f => QueryBuilders.queryString(f)).getOrElse(QueryBuilders.matchAllQuery())
  client.prepareSearch("stuff")
   .setQuery(query)
   .setSize(20)
  })
  Async {
  searchResponse.map(results => {
   Ok(views.html.index(queryString, results))
  })
  }
 }
}
```
Add to routes:
```
GET  /     controllers.Application.index(q:Option[String])
```

The final step is to use the Play templating framework to render the search results.

## JSON
You can transform ES responses into Play Json objects.

Import the implicit Writes object
```
import stretchy.XContentJson._
```

Call Json.toJson
```
val searchResponse = ES.execute(client => {...})
searchResponse.map(results => {
 val json = Json.toJson(results)
 val jsonTransformer = (__ \ 'facets \ "field-stats" \ "terms" ).json.pick

 json.transform(jsonTransformer).fold(
 valid = ( result => Ok(result) ),
 invalid = ( e => BadRequest(e.toString) )
 )
})
```
You now have a Play JSON object you can use with the JSON API or return as a result.

## Client Configuration
TODO
Two types of clients can be configured Node and Transport. See the elastic search documentation for details.
### Node Client
This client creates a local node in the Play JVM.

This is the easiest way to get started using ES. It will create a fully functioning ES node that will index data and store
it on the local filesystem.

```
elasticsearch = {

 client {
  clusterName: mycluster
  node: {
   local:true
   data:true
   settings: {
   test.prop: true
   }
  }
 },
```
* **clusterName** - Name of  cluster. Defaults to 'elasticsearch'.
* **local** - true means this node will be local to the Play JVM it will not attempt to join a  cluster.
* **data** - true to store data on this node.
* **settings** - additional properties to pass to the elasticsearch when it creates the node.

### Transport Client
Transport Client will connect to a elastic search cluster running on other servers.
```
elasticsearch = {
 client {
  clusterName: mycluster
  transport: {
  transportAddresses: ["jeremy-laptop:9300", "anotherserver:9300" ]
  settings: {
   test.prop: true
  }
  }
 },
```

*clusterName* - Name of cluster. Defaults to 'elasticsearch'.
*transportAddresses* - A list of host:port strings defining the nodes this client should connect to.
*settings* - additional properties to pass to the elasticsearch when it creates the client.

## Index Mappings
TODO
Stretchy allows you to set up  the  indicies and  types  directly in  the  Play config.

### Indcies

```

   indicies:  [
   {
       name: osl
       deleteIndex: true
       createIndex: true
       mappings: ${logEntryMappings}

   },
   {
       name: local
       deleteIndex: true
       createIndex: true
       mappings: ${logEntryMappings}

   },
   indicies:  [
   {
       name: osl
       deleteIndex: true
       createIndex: true
       mappings: ${logEntryMappings}

   },
   {
       name: local
       deleteIndex: true
       createIndex: true
       mappings: ${logEntryMappings}

   },
   indicies:  [
   {
       name: osl
       deleteIndex: true
       createIndex: true
       mappings: ${logEntryMappings}

   },
   {
       name: local
       deleteIndex: true
       createIndex: true
       mappings: ${logEntryMappings}

   },


### Type Mappings
