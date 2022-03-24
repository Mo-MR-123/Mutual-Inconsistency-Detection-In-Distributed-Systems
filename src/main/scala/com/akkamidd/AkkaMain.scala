package com.akkamidd

import akka.actor.typed.{ActorRef, ActorSystem}
import com.akkamidd.actors.MasterSite
import com.akkamidd.actors.MasterSite.{ FileUpdateConfirm, MasterSiteProtocol, Merge, SpawnSite}
import com.akkamidd.actors.Site.SiteProtocol
import org.slf4j.Logger

object AkkaMain extends App {

  def callMerge(
                 masterSystem: ActorSystem[MasterSiteProtocol],
                 sitesPartitionedList: List[Set[String]],
                 partToMerge: Set[String],
                 timeout: Long
               ): List[Set[String]] =
  {
    val newPartitionList = mergePartition(sitesPartitionedList, partToMerge)
    masterSystem.log.info("Merge, new PartitionList: {}", newPartitionList)
    printCurrentNetworkPartition(newPartitionList, masterSystem.log)

    masterSystem ! Merge()

    Thread.sleep(timeout)

    newPartitionList
  }

  def callSplit(
                 masterSystem: ActorSystem[MasterSiteProtocol],
                 sitesPartitionedList: List[Set[String]],
                 partToSplit: Set[String],
                 timeout: Long
               ): List[Set[String]] =
  {
    val newPartitionList = splitPartition(sitesPartitionedList, partToSplit)
    masterSystem.log.info("Split, new PartitionList: {}", newPartitionList)
    printCurrentNetworkPartition(newPartitionList, masterSystem.log)

    Thread.sleep(timeout)

    newPartitionList
  }

  //find the partition that the part is in
  def splitPartition(
                      sitesPartitionedList: List[Set[String]],
                      partToSplit: Set[String]
                    ): List[Set[String]] =
  {
    var newPartitionList:List[Set[String]] = sitesPartitionedList
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

  def mergePartition(
                      sitesPartitionedList: List[Set[String]],
                      partToMerge: Set[String]
                    ): List[Set[String]] =
  {
    var setsToMerge: List[Set[String]] = List()
    var newPartitionList: List[Set[String]] = sitesPartitionedList

    if(partToMerge.isEmpty) {
      return newPartitionList
    }

    var numberOfSitesInFoundSets = 0
    for(set <- sitesPartitionedList) {
      if(set.subsetOf(partToMerge)) {
        // get the sets which need to be merged
        setsToMerge = setsToMerge :+ set
        // remove the set for the merge
        newPartitionList = newPartitionList.filter(!_.equals(set))

        numberOfSitesInFoundSets = numberOfSitesInFoundSets + set.size
      }
    }
    // numberOfSitesInFoundSets should be equal to the number of sites in the partToMerge set
    if(numberOfSitesInFoundSets != partToMerge.size) {
      throw new Exception("Not valid site set for merging: the partitions that need to be merge do not contain all the sites given in partToMerge")
    }

    newPartitionList :+ partToMerge
  }

  def printCurrentNetworkPartition(
                                    sitesPartitionedList: List[Set[String]],
                                    logger: Logger
                                  ): Unit =
  {
    val result = new StringBuilder()

    result.append("The network partition is: " )
    for(set <- sitesPartitionedList) {
      result.append("{")
      for(site <- set) {
        result.append(site + ",")
      }
      // Remove last comma
      result.deleteCharAt(result.length() - 1)
      result.append("},")
    }
    // Remove last comma
    result.deleteCharAt(result.length()  - 1)
    logger.info(result.toString())
  }

  // given a site "from", find a partition that the site is currently in
  def findPartitionSet(
                        from: ActorRef[SiteProtocol],
                        sitesPartitionedList: List[Set[ActorRef[SiteProtocol]]]
                      ): Set[ActorRef[SiteProtocol]] =
  {
    for (set <- sitesPartitionedList) {
      if (set.contains(from)) {
        return set
      }
    }
    // if the site is not found in partitionList , return a empty set
    Set[ActorRef[SiteProtocol]]()
  }

  // upload files
  val time_a1 = System.currentTimeMillis().toString

  var partitionList: List[Set[String]] = List()

  val masterSite: ActorSystem[MasterSiteProtocol] = ActorSystem(MasterSite(), "MasterSite")

  // TODO: Make a function that loops through the amount of actors that need to be spawned from command line and spawn all
  //  actors needed.
  masterSite ! SpawnSite("A")
  masterSite ! SpawnSite("B")
  masterSite ! SpawnSite("C")
  masterSite ! SpawnSite("D")

  partitionList = Set("A", "B", "C", "D") +: partitionList

  println(partitionList)

  Thread.sleep(500)

  masterSite ! FileUploadMaster(time_a1)

  // split into {A,B} {C,D}

  callSplit(masterSite, partitionList, Set("A", "B", "C", "D"), 500)

  masterSite ! FileUpdateMaster(time_a1)

  Thread.sleep(500)

  //  merge into {A, B, C, D}
  callMerge(masterSite, partitionList, Set("A", "B"), 500)

  masterSite ! FileUpdateConfirm(time_a1)

  masterSite ! FileUpdateConfirm(time_a1)
}

// merge {A} , {B} in {A} {B, C} {D} -> {A, B, C} {D}
// siteA ! Merged(siteB, )
//            siteB ! CheckInconsistency(fileListA)
//                  1- Call ID for inconsistency checking (fileListA, fileListB) -> new fileList
//                  2- Broadcast(ReplaceFileList(newFileList), context.self,

// sbt AkkaMain.scala 24 upload-20 update-0 update-1 split-10 split-15
// split-10 = List(Set(0, 1,.... 10), Set(11, 12, ... 24))
// split-15 = List(Set(0, 1,.... 10), Set(11, 12, 13, 14, 15), Set(16, 17, ... 24))
// split-16 = List(Set(0, 1,.... 10), Set(11, 12, 13, 14, 15), Set(16), Set(17, ... 24))
// split-24 = List(Set(0, 1,.... 10), Set(11, 12, 13, 14, 15), Set(16), Set(17, ... 23), Set(24))