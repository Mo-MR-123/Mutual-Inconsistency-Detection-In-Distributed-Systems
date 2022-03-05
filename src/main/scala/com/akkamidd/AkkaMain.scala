package com.akkamidd

import akka.actor.AbstractActor
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, TypedActorContext}
import akka.actor.typed.scaladsl.Behaviors
import com.akkamidd.GreeterMain.SayHello

import scala.collection.mutable.Map

object AkkaMain extends App {
  val greeterMain: ActorSystem[GreeterMain.SayHello] = ActorSystem(GreeterMain(), "AkkaMain")
  greeterMain ! SayHello("Charles")
}

//object GreeterBot {
//
//  def apply(max: Int): Behavior[Site.Greeted] = {
//    bot(0, max)
//  }
//
//  private def bot(greetingCounter: Int, max: Int): Behavior[Site.Greeted] =
//    Behaviors.receive { (context, message) =>
//      val n = greetingCounter + 1
//      context.log.info("Greeting {} for {}", n, message.whom)
//      if (n == max) {
//        Behaviors.stopped
//      } else {
//        message.from ! Site.Greet(message.whom, context.self)
//        bot(n, max)
//      }
//    }
//}

// the master actor who spawn the sites
object GreeterMain {

}


