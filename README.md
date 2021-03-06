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
    "play-stretchy" %% "play-stretchy" % "0.0.4"
  )

  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
    resolvers += Resolver.url("jgh GitHub Repository", url("http://jgh.github.com/releases/"))(Resolver.ivyStylePatterns)
  )
```

### Configure your application to use the plugin

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
  indices: [
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
 first document this is indexed. To define these mappings yourself see [Index Mappings](#index-mappings). By default to index will be stored in the 'data' subdirectory of the project directory.

See [Client Configuration](#client-configuration) for how to connect to existing elasticsearch nodes

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

We using `ES.execute` to execute an action against ES. execute takes a Client => ActionRequestBuilder function. The client was configured
in the application.conf. You use the client to prepare a ActionRequestBuilder. All actions you can perform against ES have a builder class
that implements this interface. execute executes the action and returns a Future eventually containing the result.

Add to routes and we are done:
```
GET /  controllers.Application.index(q:Option[String])
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

### JSON Writable
If you simply want to proxy the ES server and return the ES response as JSON then use the XContentWriteable

The following example uses Play content negotiation to return html or ES json based accept headers:
```
ES.execute(_.prepareGet(indexName, type, id)).map(rs => {
  import stretchy.XContentWriteable._
  render {
    case Accepts.Html() => {
      Ok(views.html.index(rs)
    }
    case Accepts.Json() => {
      //This returns the ES JSON
      Ok(rs)
    }
  }
}
```
## View Helper
There is a stretchy.HitsViewHelper. This  wraps a SearchResponse  and  makes the  results easier to  use in  the Play HTML  templates.

See  the  sample app  for examples.

## Client Configuration
Two types of clients can be configured Node and Transport. See the elastic search [java client]((http://www.elasticsearch.org/guide/reference/java-api/client/) documentation for details.
### Node Client
This client creates an node in the Play JVM.

This is the easiest way to get started using ES. It can create a fully functioning ES node that will index data and store it on the local filesystem.

```
elasticsearch = {
  client  {
    clusterName: mycluster
    node: {
      local:true
      data:true
      settings: {
        test.prop: true
      }
    }
  }
}
```
* **clusterName** - Name of cluster. Defaults to 'elasticsearch'.
* **local** - true means this node will be local to the Play JVM it will not attempt to join a cluster.
* **data** - true to store data on this node. otherwise operations will be delegated to other nodes in the the 'clusterName' cluster.
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

* **clusterName** - Name of cluster to connect to. Defaults to 'elasticsearch'.
* **transportAddresses** - A list of host:port strings defining the nodes this client should connect to.
* **settings** - additional properties to pass to the elasticsearch when it creates the client.

## Index Mappings
Stretchy allows you to set up the indices and types directly in the Play config.

### Indices


Define the required indices as a json array under elasticsearch.
```
elasticsearch = {
  client: {...},
  indices: [
  {
    name: myindex
    deleteIndex: true
    createIndex: true
    mappings: ${logEntryMappings}

  },
  {
    name: myotherindex
    deleteIndex: true
    createIndex: true
    mappings: ${logEntryMappings}
  }
  ]
}
```
* **name** - Name of the index.
* **deleteIndex** - This index will deleted everytime the Play app is restarted/reloaded. Useful for dev but you probably don't want to do this in production.
* **createIndex** - The index will be created each time the Play app is restarted/reloaded.
* **mappings** - defines the type mappings for this index. See the next section. Note we have used Play config references to define our mappings in one place for both indices

### Type Mappings

In each element of the indices array you can define a mappings field. This field contains a JSON object. Stretchy will loop through each field of the mappings object and make a request to the [put mapping api](http://www.elasticsearch.org/guide/reference/api/admin-indices-put-mapping/). The name of the field is used as the type to create a mapping for. A JSON object is created contain only the single field and this object is passed to ES as the source of a PutMappingRequest.
See ES [mapping reference](http://www.elasticsearch.org/guide/reference/mapping/) for details.


```
elasticsearch = {
  client {...}
  indices: [
  {
    name: stuff
    mappings {
      "thing" : {
      "properties" : {
        "name" : {
        "type" : "string",
        },
        "description" : {
        "type" : "string",
        }
      }
      },
      "anotherthing" : {
      "properties" : {
        "size" : {
        "type" : "string",
        },
        "colour" : {
        "type" : "string",
        }
      }
      }
    }
  }]
}

```

*Note* This could result in a error if ES is unable to merge the mapping with the mapping in the current index.

## ES  Plugins

Installation  of  ES plugins is supported through a SBT plugin.

> The scientist gave a superior smile before replying, "But how  do  I install that  plugin?"
>
>  "You're very clever, young man, very clever," said the old lady. "But it's plugins all the way down!"

### Configure

In project/plugins.sbt add
```
resolvers += Resolver.url("jgh GitHub Repository", url("http://jgh.github.com/releases/"))(Resolver.ivyStylePatterns)

addSbtPlugin("play-stretchy" % "sbt-stretchy" % "0.0.4")
```

in the application build  file add  the plugins  to  be installed
```
val main = play.Project(appName, appVersion, appDependencies)
  .settings(
    stretchy.SbtStretchyPlugin.plugins("elasticsearch/elasticsearch-mapper-attachments/1.7.0") : _*
  )
```
The strings  added  to this  setting  are the  same strings that  you would  pass  to the plugin script  when
installing a plugin into a  ES installation.  E.g.
```
plugin --install <org>/<user/component>/<version>
```
See the Elastic Search [Plugins] (http://www.elasticsearch.org/guide/reference/modules/plugins/) for more info and a list
of existing  plugins.

### Install
To install the  plugins run
```
>install-es-plugins
```
By default this  will install into the  plugins  subdirectory of  the  application's base  directory.  Any plugin directories
that are no  longer defined  in the build file  will be removed. To avoid having to download already installed plugins stretchy
keeps track of the directories the plugins are installed into (in plugins/plugins.properties).

### Stage
To stage  the  plugins
```
>stage-es-plugins
```
This  task  will  copy the plugins  to  the  target  directory. This should be run after the play stage task. The plugins will then be
available when you run target/start.

### Dist
To distribute  the  plugsins  with  the  dist zip.
```
>dist-es-plugins
```
This task  only  adds  the  plugins  to  an  existing  play dist  zip  so you need  to  run  dist  first.  e.g.
```
>;dist;dist-es-plugins
```

### Remove plugins
To remove all plugins from the plugins directory
```
>remove-es-plugins
```

## Source control
By  default the following directories  are used

*  data
*  plugins
*  logs

These should be added to your source control ignore list

## Rest Interface

When you start a node ES will automatically start the rest interface on port 9200 (9201 if 9200 is already in use).

This is useful if you need to inspect the indices.

Examples:

Run a query

`http://localhost:9200/_all/_search?pretty=true&q=test`

View nodes info

`http://localhost:9200/_nodes/?all=true&pretty=true`

View mappings

`http://localhost:9200/_all/_mapping?pretty=true`

How to turn off? Set  the  http.enabled setting  to false
```
elasticsearch  =  {
   client {
     node: {
         local:true
         data:true
         settings: {
           http.enabled: false
         }
     }
   }
```

