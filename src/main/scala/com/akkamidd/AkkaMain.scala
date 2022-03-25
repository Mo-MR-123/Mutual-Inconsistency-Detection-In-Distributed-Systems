package com.akkamidd

import akka.actor.typed.ActorSystem
import com.akkamidd.actors.MasterSite
import com.akkamidd.actors.MasterSite.{MasterSiteProtocol, Merge, SpawnSite}
import com.akkamidd.timestamp.MasterSiteTimestamp
import com.akkamidd.timestamp.MasterSiteTimestamp.{TimestampProtocol, Merge, SpawnSite}
import org.slf4j.Logger

object UtilFuncsTimestamp {
  /**
   * Send messages to MasterSite to instruct it to create a new Site Actor in the Actor System.
   * @param masterSystem Mastersite.
   * @param siteNameList List of Sitenames.
   * @param partitionList List of partitions.
   * @param timeout Timeout for sleeping threads.
   * @return creates a list of created sites.
   */
  def spawnSites(
                  masterSystem: ActorSystem[TimestampProtocol],
                  siteNameList: List[String],
                  partitionList: List[Set[String]],
                  timeout: Long
                ): List[Set[String]] =
  {
    siteNameList.foreach(siteName => {
      masterSystem ! MasterSiteTimestamp.SpawnSite(siteName)
    })

    Thread.sleep(timeout)

    siteNameList.toSet +: partitionList
  }

  def callMerge(
                 siteNameFrom: String,
                 siteNameTo: String,
                 masterSystem: ActorSystem[TimestampProtocol],
                 sitesPartitionedList: List[Set[String]],
                 partToMerge: Set[String],
                 timeoutBeforeExec: Long,
                 timeoutAfterExec: Long
               ): List[Set[String]] =
  {
    Thread.sleep(timeoutBeforeExec)

    val newPartitionList = mergePartition(sitesPartitionedList, partToMerge)
    masterSystem.log.info("[Timestamp Protocol] Merge, new PartitionList: {}", newPartitionList)
    printCurrentNetworkPartition(newPartitionList, masterSystem.log)

    masterSystem ! MasterSiteTimestamp.Merge(siteNameFrom, siteNameTo, newPartitionList)

    Thread.sleep(timeoutAfterExec)

    newPartitionList
  }

  def callSplit(
                 masterSystem: ActorSystem[TimestampProtocol],
                 sitesPartitionedList: List[Set[String]],
                 partToSplit: Set[String],
                 timeoutBeforeExec: Long,
                 timeoutAfterExec: Long
               ): List[Set[String]] =
  {
    Thread.sleep(timeoutBeforeExec)

    val newPartitionList = splitPartition(sitesPartitionedList, partToSplit)
    masterSystem.log.info("[Timestamp Protocol] Split, new PartitionList: {}", newPartitionList)
    printCurrentNetworkPartition(newPartitionList, masterSystem.log)

    Thread.sleep(timeoutAfterExec)

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
    throw new Exception("[Timestamp Protocol] Not valid sub-partition in current DAG")
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
      throw new Exception("[Timestamp Protocol] Not valid site set for merging: the partitions that need to be merge do not contain all the sites given in partToMerge")
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

  def runTimestampAlgo(): Unit = {
    val masterSiteTimestamp: ActorSystem[TimestampProtocol] = ActorSystem(MasterSiteTimestamp(), "MasterSiteTimestamp")

    var partitionList: List[Set[String]] = spawnSites(masterSiteTimestamp, List("A", "B", "C", "D"), List(), 1000)

    val time_a1 = System.currentTimeMillis().toString
    masterSiteTimestamp ! MasterSiteTimestamp.FileUploadMasterSite("A", time_a1, "test.txt", partitionList)
    masterSiteTimestamp ! MasterSiteTimestamp.FileUploadMasterSite("A", time_a1, "test2.txt", partitionList)
    // split into {A,B} {C,D}
    partitionList = callSplit(masterSiteTimestamp, partitionList, Set("A", "B"), 500, 500)

    val time_a2 = System.currentTimeMillis().toString
    masterSiteTimestamp ! MasterSiteTimestamp.FileUpdateMasterSite("A", "test.txt", time_a2, partitionList)
    masterSiteTimestamp ! MasterSiteTimestamp.FileUpdateMasterSite("B", "test.txt", time_a2, partitionList)
    masterSiteTimestamp ! MasterSiteTimestamp.FileUpdateMasterSite("C", "test.txt", time_a2, partitionList)

    //  merge into {A, B, C, D}
    partitionList = callMerge("A", "C", masterSiteTimestamp, partitionList, Set("A", "B", "C", "D"), 500, 500)
  }
}

object UtilFuncs {
  /**
   * Send messages to MasterSite to instruct it to create a new Site Actor in the Actor System.
   * @param masterSystem Mastersite.
   * @param siteNameList List of sitenames
   * @param partitionList List of partitions
   * @param timeout Timeout for the sleeping thread.
   * @return list of newly created sites.
   */
  def spawnSites(
                  masterSystem: ActorSystem[MasterSiteProtocol],
                  siteNameList: List[String],
                  partitionList: List[Set[String]],
                  timeout: Long
                ): List[Set[String]] =
  {
    siteNameList.foreach(siteName => {
      masterSystem ! MasterSite.SpawnSite(siteName)
    })

    Thread.sleep(timeout)

    siteNameList.toSet +: partitionList
  }

  def callMerge(
                 siteNameFrom: String,
                 siteNameTo: String,
                 masterSystem: ActorSystem[MasterSiteProtocol],
                 sitesPartitionedList: List[Set[String]],
                 partToMerge: Set[String],
                 timeoutBeforeExec: Long,
                 timeoutAfterExec: Long
               ): List[Set[String]] =
  {
    Thread.sleep(timeoutBeforeExec)

    val newPartitionList = mergePartition(sitesPartitionedList, partToMerge)
    masterSystem.log.info("Merge, new PartitionList: {}", newPartitionList)
    printCurrentNetworkPartition(newPartitionList, masterSystem.log)

    masterSystem ! MasterSite.Merge(siteNameFrom, siteNameTo, newPartitionList)

    Thread.sleep(timeoutAfterExec)

    newPartitionList
  }

  def callSplit(
                 masterSystem: ActorSystem[MasterSiteProtocol],
                 sitesPartitionedList: List[Set[String]],
                 partToSplit: Set[String],
                 timeoutBeforeExec: Long,
                 timeoutAfterExec: Long
               ): List[Set[String]] =
  {
    Thread.sleep(timeoutBeforeExec)

    val newPartitionList = splitPartition(sitesPartitionedList, partToSplit)
    masterSystem.log.info("Split, new PartitionList: {}", newPartitionList)
    printCurrentNetworkPartition(newPartitionList, masterSystem.log)

    Thread.sleep(timeoutAfterExec)

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

  def runVersionvectorAlgo(): Unit = {
    val masterSite: ActorSystem[MasterSiteProtocol] = ActorSystem(MasterSite(), "MasterSite")

    var partitionList: List[Set[String]] = UtilFuncs.spawnSites(masterSite, List("A", "B", "C", "D"), List(), 1000)

    // upload files
    val time_a1 = System.currentTimeMillis().toString
    masterSite ! MasterSite.FileUploadMasterSite("A", time_a1, "test.txt", partitionList)

    // split into {A,B} {C,D}
    partitionList = UtilFuncs.callSplit(masterSite, partitionList, Set("A", "B"), 500, 500)

    masterSite ! MasterSite.FileUpdateMasterSite("A", ("A", time_a1), partitionList)
    masterSite ! MasterSite.FileUpdateMasterSite("B", ("A", time_a1), partitionList)

    masterSite ! MasterSite.FileUpdateMasterSite("C", ("A", time_a1), partitionList)

    //  merge into {A, B, C, D}
    partitionList = UtilFuncs.callMerge("A", "C", masterSite, partitionList, Set("A", "B", "C", "D"), 500, 500)
  }




}

object AkkaMain extends App {
//  UtilFuncs.runVersionvectorAlgo()
  UtilFuncsTimestamp.runTimestampAlgo()
}
