package com.akkamidd.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.akkamidd.actors.Site.SiteProtocol

import scala.util.hashing.MurmurHash3

// the master actor who spawn the sites
object MasterSite {
  sealed trait MainMessage
  final case class Broadcast(msg: Site.SiteProtocol) extends MainMessage

  def apply(): Behavior[MainMessage] =
    Behaviors.setup { context =>
      // create/spawn sites
      val siteA = context.spawn(Site(), "A")
      val siteB = context.spawn(Site(), "B")

      // upload files
      val time_a1 = System.currentTimeMillis().toString
      siteA ! Site.FileUpload("A", time_a1)
      val time_b1 = System.currentTimeMillis().toString
      siteA ! Site.FileUpload("B", time_b1)
      val time_a2 = System.currentTimeMillis().toString
      siteB ! Site.FileUpload("A", time_a2)
      val time_b2 = System.currentTimeMillis().toString
      siteB ! Site.FileUpload("B", time_b2)

      // update files
      siteA ! Site.FileUpdate(MurmurHash3.stringHash("A" + time_a1).toString, 0)
      siteA ! Site.FileUpdate(MurmurHash3.stringHash("A" + time_a2).toString, 0)
      siteB ! Site.FileUpdate(MurmurHash3.stringHash("B" + time_b1).toString, 0)

      Behaviors.receiveMessage {
        case MasterSite.Broadcast(msg) =>
          context.children.foreach { child =>
            context.log.info(child.toString)
            child.unsafeUpcast[SiteProtocol] ! msg
          }
          Behaviors.same

        case message =>
          Behaviors.same

        case _ =>
          Behaviors.unhandled
      }
    }
}
