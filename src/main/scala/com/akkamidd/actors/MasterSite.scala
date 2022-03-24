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

  def findSiteGivenName(
                         siteName: String,
                         children: List[ActorRef[SiteProtocol]]
                       ): Option[ActorRef[SiteProtocol]] =
  {
    for (child <- children) {
      if (child.path.name.equals(siteName)) {
        return Some(child)
      }
    }
    None
  }

  def getPartitionActorRefSet(
                               children: List[ActorRef[SiteProtocol]],
                               partitionSetString: Set[String]
                             ): Set[ActorRef[SiteProtocol]] =
  {
    partitionSetString.map(s => {
      findSiteGivenName(s, children).get
    })
  }

  def masterSiteReceive(
                         context: ActorContext[MasterSiteProtocol],
                         children: List[ActorRef[SiteProtocol]]
                       )
  : Behaviors.Receive[MasterSiteProtocol] = Behaviors.receiveMessage {

    case Broadcast(msg: SiteProtocol, from: ActorRef[SiteProtocol], partitionSet: Set[ActorRef[SiteProtocol]]) =>
      partitionSet.foreach { child =>
        if(!child.equals(from)) {
          child ! msg
          context.log.info("from {} , send message to {}", from, child.toString)
        }
      }
      masterSiteReceive(context, children)

    case FileUploadMaster(to: String, time_a1: String, partitionSet: Set[String]) =>
      // TODO: fetch the correct actorRef corresponding to the `to` name
      val site = findSiteGivenName(to, children).get
      val partitionSetRefs = getPartitionActorRefSet(children, partitionSet)

      site ! Site.FileUpload(time_a1, context.self, "test.txt", partitionSetRefs)

      masterSiteReceive(context, children)

    case FileUpdateMaster(to: String, time_a1: String, partitionSet: Set[String]) =>
      val site = findSiteGivenName(to, children).get
      val partitionSetRefs = getPartitionActorRefSet(children, partitionSet)

      site ! Site.FileUpdate(("A", time_a1), context.self, partitionSetRefs)

      masterSiteReceive(context, children)

    case Merge() =>
      val siteA = findSiteGivenName("A", partitionList)
      val siteC = findSiteGivenName("C", partitionList)

      val partitionSet3 = findPartitionSet(siteA, partitionList)
      siteA ! Merged(siteC, context.self, partitionSet3)

      masterSiteReceive(context, children)

    case FileUpdateConfirm(time_a1) =>
      val siteA = findSiteGivenName("A", partitionList)
      val partitionSet3 = findPartitionSet(siteA, partitionList)
      siteA ! Site.FileUpdate(("A", time_a1), context.self, partitionSet3)

      masterSiteReceive(context, children)

    // create/spawn sites
    case SpawnSite(siteName: String) =>
      val spawnedSite = context.spawn(Site(), siteName)
      val newChildren = spawnedSite +: children
      context.log.info(s"$newChildren")
      masterSiteReceive(context, newChildren)
  }

  def apply(): Behavior[MasterSiteProtocol] = Behaviors.setup {
    context => masterSiteReceive(context, List())
  }

}
