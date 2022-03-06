package com.akkamidd.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.akkamidd.actors.Site.FileMessages

import scala.util.hashing.MurmurHash3

// the master actor who spawn the sites
object MasterSite {
  sealed trait MainMessage
  final case class Broadcast(msg: Site.FileMessages) extends MainMessage

  def apply(): Behavior[MainMessage] =
    Behaviors.setup { context =>
      // create/spawn sites
      val siteA = context.spawn(Site(), "A")
      val siteB = context.spawn(Site(), "B")

      // upload files
      siteA ! Site.FileUpload("A", "test")
      siteA ! Site.FileUpload("B", "test2")
      siteB ! Site.FileUpload("A", "test")
      siteB ! Site.FileUpload("B", "test3")

      // update files
      siteA ! Site.FileUpdate(MurmurHash3.stringHash("A" + "test").toString, 0)
      siteA ! Site.FileUpdate(MurmurHash3.stringHash("A" + "test2").toString, 0)
      siteB ! Site.FileUpdate(MurmurHash3.stringHash("B" + "test3").toString, 0)

      Behaviors.receiveMessage {
        case MasterSite.Broadcast(msg) =>
          context.children.foreach { child =>
            context.log.info(child.toString)
            child.unsafeUpcast[FileMessages] ! msg
          }
          Behaviors.same

        case message =>
          Behaviors.same

        case _ =>
          Behaviors.unhandled
      }
    }
}
