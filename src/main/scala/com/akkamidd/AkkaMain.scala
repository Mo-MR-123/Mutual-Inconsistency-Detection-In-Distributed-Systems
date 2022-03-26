package com.akkamidd

import akka.actor.typed.ActorSystem
import com.akkamidd.actors.MasterSite
import com.akkamidd.actors.MasterSite.{FileUpdateMasterSite, FileUploadMasterSite, MasterSiteProtocol, Merge, SpawnSite}
import org.slf4j.Logger


object AkkaMain extends App {
  println(args.length)
  println(args.mkString("Array(", ", ", ")"))

  if (args.length == 1) { // run experiments
    args(0) match {
      case "0" =>
        Experiments.experiment0(1000, 500, 500)
    }
  } else {
    val numSiteActors = args(0).toLong

//    if (numSiteActors < 2) {
//      throw new Exception("It is not possible to run less than 2 Site Actors. Please provide 2 or more Site Actors.")
//    }

    val masterSystem = ActorSystem(MasterSite(), "MasterSite")
    val partitionList = UtilFuncs.spawnSites(masterSystem, List.range(0, numSiteActors).map(_.toString), 2000)

    args.tail.foreach { // run
      case command if command.contains("upload") =>
        val splitCommand = command.split("-").tail
        val currTimestamp = System.currentTimeMillis().toString
        val siteToUpload = splitCommand(0)
        val fileName = splitCommand(1)
        masterSystem ! FileUploadMasterSite(siteToUpload, currTimestamp, fileName, partitionList)


    }

    masterSystem.terminate()
  }

}

object Experiments {
  def experiment0(
                   spawningActorsTimeout: Long,
                   timeoutSplit: Long,
                   timeoutMerge: Long
                 ): Unit =
  {
    val experimentStartMillis = System.currentTimeMillis()

    val masterSite: ActorSystem[MasterSiteProtocol] = ActorSystem(MasterSite(), "MasterSite")

    var partitionList: List[Set[String]] = UtilFuncs.spawnSites(masterSite, List("A", "B", "C", "D"), spawningActorsTimeout)

    // upload files
    val time_a1 = System.currentTimeMillis().toString
    masterSite ! FileUploadMasterSite("A", time_a1, "test.txt", partitionList)

    // split into {A,B} {C,D}
    partitionList = UtilFuncs.callSplit(masterSite, partitionList, Set("A", "B"), timeoutSplit, timeoutSplit)

    masterSite ! FileUpdateMasterSite("A", ("A", time_a1), partitionList)
    masterSite ! FileUpdateMasterSite("B", ("A", time_a1), partitionList)

    masterSite ! FileUpdateMasterSite("C", ("A", time_a1), partitionList)

    //  merge into {A, B, C, D}
    partitionList = UtilFuncs.callMerge("A", "C", masterSite, partitionList, Set("A", "B", "C", "D"), timeoutMerge, timeoutMerge)

    val experimentEndMillis = System.currentTimeMillis() - timeoutMerge*2 - timeoutSplit*2 - spawningActorsTimeout

    masterSite.terminate()

    println(s"[Experiment 0] Execution time in millis: ${experimentEndMillis - experimentStartMillis}")
  }
}


object UtilFuncs {
  /**
   * Send messages to MasterSite to instruct it to create a new Site Actor in the Actor System.
   * @param masterSystem The Actor System that is used to send messages to the MasterSite
   * @param siteNameList The names of the Site Actors to spawn
   * @param timeout The incurred timeout after sending the Spawn Messages to the master sites to
   *                ensure all sites are spawned before continuing with other operations on the system
   * @param partitionList The initial partition list to use, List() by default
   * @return The partition list that contains 1 set containing the names of the spawned sites.
   *         We assume that all Site Actors are initially in 1 partition.
   */
  def spawnSites(
                  masterSystem: ActorSystem[MasterSiteProtocol],
                  siteNameList: List[String],
                  timeout: Long,
                  partitionList: List[Set[String]] = List(),
                ): List[Set[String]] =
  {
    siteNameList.foreach(siteName => {
      masterSystem ! SpawnSite(siteName)
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

    masterSystem ! Merge(siteNameFrom, siteNameTo, newPartitionList)

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
}
