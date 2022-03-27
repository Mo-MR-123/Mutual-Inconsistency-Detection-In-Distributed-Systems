package com.akkamidd

import akka.actor.typed.ActorSystem
import com.akkamidd.actors.MasterSite
import com.akkamidd.actors.MasterSite.{FileUpdateMasterSite, FileUploadMasterSite, MasterSiteProtocol, Merge, SpawnSite}
import org.slf4j.Logger

import scala.io.StdIn


object AkkaMain extends App {
  val numSiteActors = args(0).toLong
  val siteActorNames = List.range(0, numSiteActors).map(_.toString)

  val masterSystem = ActorSystem(MasterSite(false), "MasterSite")
  var partitionList = UtilFuncs.spawnSites(masterSystem, siteActorNames, 3000)

  println(s"$numSiteActors Site actors spawned successfully! Names of all actors: $siteActorNames")
  println(s"The partition list is $partitionList")

  while (true) {
    StdIn.readLine match {
      case command if command contains "upload" =>
        val uploadCommand = command.split("-").tail
        val currTimestamp = System.currentTimeMillis().toString
        val siteToUpload = uploadCommand(0)
        val fileName = uploadCommand(1)

        UtilFuncs.callUploadFile(siteToUpload, currTimestamp, masterSystem, fileName, partitionList)

      case command if command contains "update" =>
        val updateCommand = command.split("-").tail
        val siteToUpload = updateCommand(0)
        val originPointerParts = updateCommand(1).tail.dropRight(1).split(",")
        val originPointer = (originPointerParts(0).trim, originPointerParts(1).trim)

        UtilFuncs.callUpdateFile(siteToUpload, originPointer, masterSystem, partitionList)

      case command if command contains "split" =>
        val splitCommand = command.split("-").tail

        if (splitCommand.contains("{") && splitCommand.contains("}")) {
          val siteNamesToSplit = splitCommand(0).tail.dropRight(1).split(",")
          println(siteNamesToSplit)
          if (splitCommand.length == 2) { // e.g. split-10-1000
            val timeoutValue = splitCommand(1).toLong
            partitionList = UtilFuncs.callSplit(masterSystem, partitionList, siteNamesToSplit.toSet, timeoutValue, timeoutValue)
          } else {
            partitionList = UtilFuncs.callSplit(masterSystem, partitionList, siteNamesToSplit.toSet, 1000, 1000)
          }
        } else {
          val siteNameToSplit = splitCommand(0)
          if (splitCommand.length == 3) {
            val timeoutValue = splitCommand(1).toLong
            partitionList = UtilFuncs.callSplit(masterSystem, partitionList, siteNameToSplit, timeoutValue, timeoutValue)
          } else {
            partitionList = UtilFuncs.callSplit(masterSystem, partitionList, siteNameToSplit, 1000, 1000)
          }
        }

      case command if command contains "merge" =>
        val mergeCommand = command.split("-").tail
        val siteFrom = mergeCommand(0)
        val siteTo = mergeCommand(1)
        val siteNamesToMerge = mergeCommand(2).tail.dropRight(1).split(",").toSet // e.g. marge-0-11-{10,11,12}-1000

        if (mergeCommand.length == 4) {
          val timeoutValue = mergeCommand(3).toLong
          partitionList = UtilFuncs.callMerge(siteFrom, siteTo, masterSystem, partitionList, siteNamesToMerge, timeoutValue, timeoutValue)
        } else {
          partitionList = UtilFuncs.callMerge(siteFrom, siteTo, masterSystem, partitionList, siteNamesToMerge, 1000, 1000)
        }

      case "quit" =>
        UtilFuncs.terminateSystem(masterSystem, 1000)
        System.exit(0)

      case _ =>
        println("Not a valid command!")
    }
  }

  UtilFuncs.terminateSystem(masterSystem, 1000)

}


object UtilFuncs {

  def terminateSystem(actorSystem: ActorSystem[MasterSiteProtocol], timeoutAfterTermination: Long): Unit = {
    actorSystem.terminate()
    Thread.sleep(timeoutAfterTermination)
  }

  def callUploadFile(
                  siteName: String,
                  timestamp: String,
                  masterSystem: ActorSystem[MasterSiteProtocol],
                  fileName: String,
                  partitionList: List[Set[String]]
                ): Unit =
  {
    masterSystem ! FileUploadMasterSite(siteName, timestamp, fileName, partitionList)
  }

  def callUpdateFile(
                  siteName: String,
                  originPointer: (String, String),
                  masterSystem: ActorSystem[MasterSiteProtocol],
                  partitionList: List[Set[String]]
                ): Unit =
  {
    masterSystem ! FileUpdateMasterSite(siteName, originPointer, partitionList)
  }

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
//    masterSystem.log.info("Merge, new PartitionList: {}", newPartitionList)
    printCurrentNetworkPartition(newPartitionList, masterSystem.log)

    masterSystem ! Merge(siteNameFrom, siteNameTo, newPartitionList)

    Thread.sleep(timeoutAfterExec)

    newPartitionList
  }

  def callSplit(
                 masterSystem: ActorSystem[MasterSiteProtocol],
                 sitesPartitionedList: List[Set[String]],
                 siteAtWhichSplit: String,
                 timeoutBeforeExec: Long,
                 timeoutAfterExec: Long
               ): List[Set[String]] =
  {
    Thread.sleep(timeoutBeforeExec)

    val newPartitionList = splitPartition(sitesPartitionedList, siteAtWhichSplit)

    Thread.sleep(timeoutAfterExec)

    printCurrentNetworkPartition(newPartitionList, masterSystem.log)

    newPartitionList
  }

  //find the partition that the part is in
  def splitPartition(
                      sitesPartitionedList: List[Set[String]],
                      siteAtWhichSplit: String
                    ): List[Set[String]] =
  {
    var newPartitionList:List[Set[String]] = sitesPartitionedList

    for (set <- newPartitionList){
      if (set.contains(siteAtWhichSplit)) {
        // the sites whose number is bigger than siteAtWhichSplit should be splitted away
        val partToSplit = set.filter(_>siteAtWhichSplit)
        // if the site is the biggest in the current partition, then nothing to split
        if (partToSplit.isEmpty){
          return sitesPartitionedList
        }
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
    } else if (setsToMerge.length != 2) {
      throw new Exception(s"Merging should happen over two paritions, ${setsToMerge.length} paritions were specified")
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

//object Experiments {
//  def experiment0(
//                   debugMode: Boolean,
//                   spawningActorsTimeout: Long,
//                   timeoutSplit: Long,
//                   timeoutMerge: Long
//                 ): Unit =
//  {
//    val experimentStartMillis = System.currentTimeMillis()
//
//    val masterSite: ActorSystem[MasterSiteProtocol] = ActorSystem(MasterSite(debugMode), "MasterSite")
//
//    var partitionList: List[Set[String]] = UtilFuncs.spawnSites(masterSite, List("A", "B", "C", "D"), spawningActorsTimeout)
//
//    // upload files
//    val time_a1 = System.currentTimeMillis().toString
//    UtilFuncs.callUploadFile("A", time_a1, masterSite, "test.txt", partitionList)
////    masterSite ! FileUploadMasterSite("A", time_a1, "test.txt", partitionList)
//
//    // split into {A,B} {C,D}
//    partitionList = UtilFuncs.callSplit(masterSite, partitionList, Set("A", "B"), timeoutSplit, timeoutSplit)
//
//    UtilFuncs.callUpdateFile("A", ("A", time_a1), masterSite, partitionList)
//    UtilFuncs.callUpdateFile("B", ("A", time_a1), masterSite, partitionList)
//    UtilFuncs.callUpdateFile("C", ("A", time_a1), masterSite, partitionList)
////    masterSite ! FileUpdateMasterSite("C", ("A", time_a1), partitionList)
//
//    //  merge into {A, B, C, D}
//    partitionList = UtilFuncs.callMerge("A", "C", masterSite, partitionList, Set("A", "B", "C", "D"), timeoutMerge, timeoutMerge)
//
//    val experimentEndMillis = System.currentTimeMillis() - timeoutMerge*2 - timeoutSplit*2 - spawningActorsTimeout
//
//    masterSite.terminate()
//
//    println(s"[Experiment 0] Execution time in millis: ${experimentEndMillis - experimentStartMillis}")
//  }
//}