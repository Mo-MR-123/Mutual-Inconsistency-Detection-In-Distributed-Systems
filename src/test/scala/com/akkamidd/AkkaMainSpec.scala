//#full-example
package com.akkamidd

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.akkamidd.Greeter.Greet
import com.akkamidd.Greeter.Greeted
import org.scalatest.wordspec.AnyWordSpecLike

class AkkaMainSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "A Greeter" must {
    "reply to greeted" in {
      val replyProbe = createTestProbe[Greeted]()
      val underTest = spawn(Greeter())
      underTest ! Greet("Santa", replyProbe.ref)
      replyProbe.expectMessage(Greeted("Santa", underTest.ref))
    }
  }
}
