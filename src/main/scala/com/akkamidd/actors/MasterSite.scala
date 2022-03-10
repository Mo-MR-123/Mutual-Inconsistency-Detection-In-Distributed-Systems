package com.akkamidd.actors
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.akkamidd.actors.Site.SiteProtocol

import scala.util.hashing.MurmurHash3

// the master actor who spawn the sites
object MasterSite {
  sealed trait MasterSiteProtocol
  final case class Broadcast(msg: Site.SiteProtocol, from: ActorRef[Site.SiteProtocol]) extends MasterSiteProtocol

  def apply(): Behavior[MasterSiteProtocol] =
    Behaviors.setup { context =>

      // create/spawn sites
      val siteA = context.spawn(Site(), "A")
      val siteB = context.spawn(Site(), "B")
      val siteC = context.spawn(Site(), "C")

      val sitesList: List[ActorRef[SiteProtocol]] = List(siteA, siteB, siteC)

      // upload files
      val time_a1 = System.currentTimeMillis().toString
      siteA ! Site.FileUpload(time_a1, context.self, "test.txt")

      println("\"A\" + time_a1   " + MurmurHash3.stringHash("A" + time_a1))

      // update files
      siteA ! Site.FileUpdate(("A", time_a1), context.self)
      siteA ! Site.FileUpdate(("A", time_a1), context.self)

//      //siteA ! Site.printMap()
//
//      context.system.scheduler.scheduleOnce(Duration(2, TimeUnit.SECONDS),siteA,Site.printMap() )
////      context.system.scheduler.scheduleOnce(Duration(2, TimeUnit.SECONDS ) {
////        siteA ! Site.printMap()
////      }

      Behaviors.receiveMessage {
        case MasterSite.Broadcast(msg: SiteProtocol, from: ActorRef[SiteProtocol]) =>
          sitesList.foreach { child =>
            child ! msg
          }
          Behaviors.same

        case message =>
          Behaviors.same

        case _ =>
          Behaviors.unhandled
      }
    }
}
