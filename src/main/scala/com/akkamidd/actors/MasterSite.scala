package com.akkamidd.actors
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.akkamidd.actors.Site.{Merged, SiteProtocol}


// the master actor who spawn the sites
object MasterSite {

  // MasterSiteProtocol - Defines the messages that dictates the protocol of the master site.
  sealed trait MasterSiteProtocol
  final case class Broadcast(
                              msg: Site.SiteProtocol,
                              from: ActorRef[Site.SiteProtocol],
                              partitionSet: Set[ActorRef[SiteProtocol]]
                            ) extends MasterSiteProtocol
  final case class FileUploadMaster(to: String, time: String, partitionSet: Set[String]) extends MasterSiteProtocol
  final case class FileUpdateMaster(to: String, time: String, partitionSet: Set[String]) extends MasterSiteProtocol
  final case class Merge() extends MasterSiteProtocol
  final case class FileUpdateConfirm(time: String) extends MasterSiteProtocol
  final case class SpawnSite(siteName: String) extends MasterSiteProtocol

  def findSiteGivenName(siteName: String, context: ActorContext[MasterSite.MasterSiteProtocol]): Option[ActorRef[Nothing]] = {
    for (child <- context.children) {
      if (child.path.name.equals(siteName)) {
        return Some(child)
      }
    }
    None
  }

  def fromPartitionList(context: ActorContext[MasterSiteProtocol])
  : Behaviors.Receive[MasterSiteProtocol] = Behaviors.receiveMessage {

    case Broadcast(msg: SiteProtocol, from: ActorRef[SiteProtocol], partitionSet: Set[ActorRef[SiteProtocol]]) =>
      partitionSet.foreach { child =>
        if(!child.equals(from)) {
          child ! msg
          context.log.info("from {} , send message to {}", from, child.toString)
        }
      }
      fromPartitionList(context)

    case FileUploadMaster(to: String, time_a1: String, partitionSet: Set[String]) =>
      // TODO: fetch the correct actorRef corresponding to the `to` name
      val site = findSiteGivenName(to, context)
      site ! Site.FileUpload(time_a1, context.self, "test.txt", partitionSet)
      fromPartitionList(context)

    case FileUpdateMaster(to: String, time_a1: String, partitionSet: Set[String]) =>
      siteA ! Site.FileUpdate(("A", time_a1), context.self, partitionSet)
      fromPartitionList(context)

    case Merge() =>
      val siteA = findSiteGivenName("A", partitionList)
      val siteC = findSiteGivenName("C", partitionList)

      val partitionSet3 = findPartitionSet(siteA, partitionList)
      siteA ! Merged(siteC, context.self, partitionSet3)

      fromPartitionList(context)

    case FileUpdateConfirm(time_a1) =>
      val siteA = findSiteGivenName("A", partitionList)
      val partitionSet3 = findPartitionSet(siteA, partitionList)
      siteA ! Site.FileUpdate(("A", time_a1), context.self, partitionSet3)
      fromPartitionList(context)

    // create/spawn sites
    case SpawnSite(siteName: String) =>
      context.spawn(Site(), siteName)
      fromPartitionList(context)
  }

  def apply(): Behavior[MasterSiteProtocol] = Behaviors.setup {
    context => fromPartitionList(context)
  }

}
