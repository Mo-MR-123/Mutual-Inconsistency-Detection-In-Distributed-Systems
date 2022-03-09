package com.akkamidd.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.akkamidd.actors.Site.SiteProtocol

import scala.util.hashing.MurmurHash3

// the master actor who spawn the sites
object MasterSite {
  sealed trait MasterSiteProtocol
  final case class Broadcast(msg: Site.SiteProtocol, from: ActorRef[Site.SiteProtocol]) extends MasterSiteProtocol

  def apply(): Behavior[MasterSiteProtocol] =
    Behaviors.setup { context =>
      // create/spawn sites
      val siteA = context.spawn(Site("A"), "A")
      val siteB = context.spawn(Site("B"), "B")
      val siteC = context.spawn(Site("C"), "C")

      // upload files
      val time_a1 = System.currentTimeMillis().toString
      siteA ! Site.FileUpload(siteA, time_a1,context.self, "test.txt")

      println("\"A\" + time_a1   " + MurmurHash3.stringHash("A" + time_a1))

      // update files
      siteA ! Site.FileUpdate(("A", time_a1), siteA)
      siteA ! Site.FileUpdate(("A", time_a1), siteA)

      Behaviors.receiveMessage {
        case MasterSite.Broadcast(msg, from: ActorRef[Site.SiteProtocol]) =>
          siteA ! msg
          siteB ! msg
          siteC ! msg
//          context.children.foreach { child =>
//            if (!child.equals(from)) {
//              context.log.info(child.toString)
//              child.unsafeUpcast[SiteProtocol] ! msg
//            }
//          }
          Behaviors.same

        case message =>
          Behaviors.same

        case _ =>
          Behaviors.unhandled
      }
    }
}
