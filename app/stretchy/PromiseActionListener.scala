package stretchy

import org.elasticsearch.action.ActionListener
import scala.concurrent.Promise

/**
 * Created with IntelliJ IDEA.
 * User: harej
 * Date: 13/06/13
 * Time: 8:56 AM
 * To change this template use File | Settings | File Templates.
 */
class PromiseActionListener[T]() extends ActionListener[T] {
  val promise: Promise[T] = scala.concurrent.promise()

  def onResponse(response: T) {
    promise.success(response)
  }

  def onFailure(e: Throwable) {
    promise.failure(e)
  }

}
