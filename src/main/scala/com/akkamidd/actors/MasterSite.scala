package com.akkamidd.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.akkamidd.actors.Site.SiteProtocol

import scala.util.hashing.MurmurHash3

// the master actor who spawn the sites
object MasterSite {
  sealed trait MainMessage
  final case class Broadcast(msg: Site.SiteProtocol, from: ActorRef[Site.SiteProtocol]) extends MainMessage

  def apply(): Behavior[MainMessage] =
    Behaviors.setup { context =>
      // create/spawn sites
      val siteA = context.spawn(Site(), "A")
      val siteB = context.spawn(Site(), "B")

      // upload files
      val time_a1 = System.currentTimeMillis().toString
      siteA ! Site.FileUpload(siteA, time_a1)
      val time_b1 = time_a1 + 1
      siteA ! Site.FileUpload(siteB, time_b1)
      val time_a2 = time_b1 + 1
      siteB ! Site.FileUpload(siteA, time_a2)
      val time_b2 = time_a2 + 1
      siteB ! Site.FileUpload(siteB, time_b2)

      println("\"A\" + time_a1   " + MurmurHash3.stringHash("A" + time_a1))
      println("\"A\" + time_a2   " + MurmurHash3.stringHash("A" + time_a2))
      println("\"B\" + time_b1   " + MurmurHash3.stringHash("B" + time_b1))

      // update files
      siteA ! Site.FileUpdate(MurmurHash3.stringHash("A" + time_a1), siteA)
      siteA ! Site.FileUpdate(MurmurHash3.stringHash("A" + time_a2), siteA)
      siteA ! Site.FileUpdate(MurmurHash3.stringHash("A" + time_a1), siteA)
      siteB ! Site.FileUpdate(MurmurHash3.stringHash("B" + time_b1), siteB)

      Behaviors.receiveMessage {
        case MasterSite.Broadcast(msg, from: ActorRef[Site.SiteProtocol]) =>
          context.children.foreach { child =>
            if (!child.eq(from)) {
              context.log.info(child.toString)
              child.unsafeUpcast[SiteProtocol] ! msg
            }
          }
          Behaviors.same

        case message =>
          Behaviors.same

        case _ =>
          Behaviors.unhandled
      }
    }
}
