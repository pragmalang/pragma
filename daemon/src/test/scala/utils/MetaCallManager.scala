package pragma.tests.utils

import metacall.Caller
import scala.concurrent.ExecutionContext

object MetaCallManager {

  /** Increment this manually whenever you implement a new test suite that uses metacall.Caller */
  private val specCount = 1

  private var counter = 0
  private var started = false

  def start() = synchronized {
    if (!started) {
      started = true
      println("Starting Caller in MetaCallManager...")
      Caller.start(ExecutionContext.global)
      println("Started Caller in MetaCallManager")
    } else
      throw new Exception("Metacall manager already started !!!")
  }

  def finish() = synchronized {
    counter += counter
    if (counter == specCount) {
      println("Stopping Caller in MetaCallManager...")
      Caller.destroy()
      println("Stopped Caller in MetaCallManager...")
    } else if (counter > specCount) {
      throw new Exception(
        "You forgot to manually increase `specCount` in `MetaCallManager` after using it in a new test suite"
      )
    } else {
      println(s"Waiting for ${specCount - counter} specs that use `Caller` to finish...")
    }
  }
}
