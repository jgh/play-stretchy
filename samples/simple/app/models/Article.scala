package models

/**
 * Created with IntelliJ IDEA.
 * User: jeremy
 * Date: 2/08/13
 * Time: 8:00 AM
 * To change this template use File | Settings | File Templates.
 */
case class Article(title:String, author:String, description:String, content:String, tags:Set[String]  =  Set.empty)
