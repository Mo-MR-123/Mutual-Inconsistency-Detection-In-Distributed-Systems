package com.akkamidd.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.akkamidd.GreeterMain

object SiteRoot {
  sealed trait MainMessage
  final case class SayHello(name: String) extends MainMessage
  final case class Broadcast() extends MainMessage
  def apply(): Behavior[MainMessage] =
    Behaviors.setup { context =>
      //#create-actors
      val siteA = context.spawn(Site(), "A")
      val siteB = context.spawn(Site(), "B")


      val childrenList= context.children
      for (i <- childrenList) {
        context.log.info(i.toString())
      }

      siteA ! Site.FileUpload("A")
      siteA ! Site.FileUpload("B")
      siteB ! Site.FileUpload("A")
      siteB ! Site.FileUpload("B")
      //#create-actors
      siteA ! Site.FileUpdate()
      siteA ! Site.FileUpdate()
      siteB ! Site.FileUpdate()

      Behaviors.receiveMessage {
        case  GreeterMain.Broadcast() =>
          context.log.info("hello")
          val childrenList= context.children
          for (i <- childrenList) {
            context.log.info(i.toString())
            i.asInstanceOf[ActorRef[Site]] ! Site.FileUpdate()
          }
          Behaviors.same
        case message =>
          context.log.info("hello")
          Behaviors.same


        //        //#create-actors
        //        val replyTo = context.spawn(GreeterBot(max = 3), message.name)
        //        //#create-actors
        //        siteA ! Site.Greet(message.name, replyTo)
        //        siteB ! Site.Greet(message.name, replyTo)

      }
    }
}
