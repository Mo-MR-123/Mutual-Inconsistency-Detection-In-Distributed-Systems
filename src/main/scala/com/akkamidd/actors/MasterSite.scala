package com.akkamidd.actors
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.akkamidd.actors.Site.SiteProtocol

import scala.reflect.Manifest.Nothing
import scala.util.hashing.MurmurHash3

// the master actor who spawn the sites
object MasterSite {
  sealed trait MasterSiteProtocol
  final case class Broadcast(msg: Site.SiteProtocol, from: ActorRef[Site.SiteProtocol]) extends MasterSiteProtocol

  //find the partition that the part is in
  def splitPartition(sitesPartitionedList: List[Set[ActorRef[SiteProtocol]]],partToSplit:Set[ActorRef[SiteProtocol]]): List[Set[ActorRef[SiteProtocol]]] ={
    var newPartitionList:List[Set[ActorRef[SiteProtocol]]] = sitesPartitionedList
    for (set <- newPartitionList){
      if (partToSplit.subsetOf(set)) {
        // remove The old partition
        newPartitionList = newPartitionList.filter(!_.equals(set))
        // create new partition for the remaining part
        val setRemain = set -- partToSplit
        newPartitionList = newPartitionList :+ setRemain
        // create new partition for the partToSplit and append the new one to partition list
        newPartitionList = newPartitionList :+ partToSplit
        return newPartitionList
      }
    }
    throw new Exception("Not valid sub-partition in current DAG")
  }

  // given a site "from", find a partition that the site is currently in
  def findPartitionSet(from: ActorRef[SiteProtocol],sitesPartitionedList: List[Set[ActorRef[SiteProtocol]]]): Set[ActorRef[SiteProtocol]] = {
    for (set <- sitesPartitionedList) {
      if (set.contains(from)) {
        return set
      }

    }
    // if the site is not found in partitionList , return a empty set
    Set[ActorRef[SiteProtocol]]()
  }

  def apply(): Behavior[MasterSiteProtocol] =
    Behaviors.setup { context =>

      // create/spawn sites
      val siteA = context.spawn(Site(), "A")
      val siteB = context.spawn(Site(), "B")
      val siteC = context.spawn(Site(), "C")
      val siteD = context.spawn(Site(), "D")

      // test for spliting
      // val sitesList: List[ActorRef[SiteProtocol]] = List(siteA, siteB, siteC, siteD)
      var init_sitePartitionList: List[Set[ActorRef[SiteProtocol]]] = List(Set(siteA,siteB,siteC,siteD))
      // split into {A,B}{C,D}
      init_sitePartitionList = splitPartition(init_sitePartitionList,Set(siteA))
      context.log.info("Split 1, new PartitionList: {}",init_sitePartitionList)

      init_sitePartitionList = splitPartition(init_sitePartitionList,Set(siteB,siteC))
      context.log.info("Split 2, new PartitionList: {}",init_sitePartitionList)


      var sitesPartitionedList: List[Set[ActorRef[SiteProtocol]]] = List(Set(siteA,siteB),Set(siteC,siteD))

      // upload files
      val time_a1 = System.currentTimeMillis().toString
      siteA ! Site.FileUpload(time_a1, context.self, "test.txt")

      println("\"A\" + time_a1   " + MurmurHash3.stringHash("A" + time_a1))

      // update files
      siteA ! Site.FileUpdate(("A", time_a1), context.self)
      siteA ! Site.FileUpdate(("A", time_a1), context.self)
      siteB ! Site.FileUpdate(("A", time_a1), context.self)
      siteC ! Site.FileUpdate(("A", time_a1), context.self)

      //      //siteA ! Site.printMap()
      //
      //      context.system.scheduler.scheduleOnce(Duration(2, TimeUnit.SECONDS),siteA,Site.printMap() )
      ////      context.system.scheduler.scheduleOnce(Duration(2, TimeUnit.SECONDS ) {
      ////        siteA ! Site.printMap()
      ////      }

      Behaviors.receiveMessage {
        case MasterSite.Broadcast(msg: SiteProtocol, from: ActorRef[SiteProtocol]) =>
          val partitionSet = findPartitionSet(from: ActorRef[SiteProtocol], sitesPartitionedList: List[Set[ActorRef[SiteProtocol]]] )
          partitionSet.foreach { child =>
            if(!child.equals(from)) {
              child ! msg
              context.log.info("from {} , send message to {}",from,child.toString)
            }
          }
          Behaviors.same

        case _ =>
          Behaviors.unhandled
      }
    }
}
