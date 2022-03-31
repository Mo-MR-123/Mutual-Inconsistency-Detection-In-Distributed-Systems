package com.akkamidd

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorSystem
import com.akkamidd.actors.MasterSite
import com.akkamidd.actors.MasterSite.MasterSiteProtocol
import org.scalatest.wordspec.AnyWordSpecLike

class Experiment3 extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "Experiment 3" must {
    "Detect Inconsistency" in {

      def randomString(length: Int) = {
        val r = new scala.util.Random
        val sb = new StringBuilder
        for (_ <- 1 to length) {
          sb.append(r.nextPrintableChar)
        }
        sb.toString
      }

      val spawningActorsTimeout = 1000
      val timeoutSplit = 1000
      val timeoutMerge = 1000

      val experimentStartMillis = System.currentTimeMillis
      val masterSite: ActorSystem[MasterSiteProtocol] = ActorSystem(MasterSite(debugMode = false), "MasterSite")

      val listSiteNames = List.range(0, 75).map("Site" + _.toString)
      var listOriginPointers = Map[String, String]()

      var partitionList: List[Set[String]] = UtilFuncs.spawnSites(masterSystem = masterSite, siteNameList = listSiteNames, timeout = spawningActorsTimeout)

      var thresholdSplit = 6
      var thresholdMerge = 4

      val random = scala.util.Random
      // Can never have more merges than splits so only needs to check whether the merge threshold has been reached.
      while (thresholdMerge > 0) {
        val randomValue = random.nextInt(100) // 0 to 100

        randomValue match {
          // Upload
          case x if x <= 25 =>
            val randomSite = listSiteNames(random.nextInt(50))
            val time = System.currentTimeMillis().toString
            listOriginPointers = listOriginPointers + (randomSite -> time)
            val fileName = randomString(5) + ".txt"
            UtilFuncs.callUploadFile(randomSite, time, masterSite, fileName, partitionList)

          // Update
          case x if x > 25 && x <= 50 =>
            val randomSite = listSiteNames(random.nextInt(50))
            val randomFileIndex = random.nextInt(listOriginPointers.size)
            val tuple = listOriginPointers.toList(randomFileIndex)
            UtilFuncs.callUpdateFile(randomSite, tuple, masterSite, partitionList)

          // Split
          case x if x > 50 && x <= 75 =>
            if (thresholdSplit != 0) {
              val randomSite = listSiteNames(random.nextInt(50))
              val previousPartitionList = partitionList
              partitionList = UtilFuncs.callSplit(masterSite, partitionList, randomSite, timeoutSplit, timeoutSplit)
              if (!previousPartitionList.equals(partitionList)) {
                thresholdSplit = thresholdSplit - 1
              }
            }

          // Merge
          case x if x > 75 && x < 100 =>
            if (thresholdMerge != 0) {
              val randomSite1 = listSiteNames(random.nextInt(50))
              val randomSite2 = listSiteNames(random.nextInt(50))
              val previousPartitionList = partitionList
              partitionList = UtilFuncs.callMerge(randomSite1, randomSite2, masterSite, partitionList, timeoutMerge, timeoutMerge)
              if (!previousPartitionList.equals(partitionList)) {
                thresholdMerge = thresholdMerge - 1
              }
            }

        }
      }

      val estimatedTime = System.currentTimeMillis - experimentStartMillis
      masterSite.log.info("Experiment 3 ended - time: " + estimatedTime.toString)
    }
  }
}