package daemon

import utils._
import cats.effect.IO
import cats.implicits._

import org.scalatest.funsuite.AnyFunSuite

class TransactionSpec extends AnyFunSuite {

  test("daemon.utils.transaction works") {
    var number = 0
    def step(n: Int, fail: Boolean = false) = {
      val run = IO {
        if (!fail)
          number += 1
        else
          throw new Exception(s"Step $n FAILED")
      }

      val rollback = IO(number -= 1)
      Step(run, rollback)
    }

    val step1 = step(1)
    val step2 = step(2)
    val step3 = step(3, true)
    val step4 = step(4)
    val step5 = step(5)
    transaction(List(step1, step2, step3, step4, step5)).run.void
      .handleError(_ => ())
      .unsafeRunSync()

    assert(number == 0)
  }
}
