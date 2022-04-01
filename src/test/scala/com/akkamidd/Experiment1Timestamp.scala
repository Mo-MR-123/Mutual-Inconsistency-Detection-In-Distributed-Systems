package com.akkamidd

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorSystem
import com.akkamidd.timestamp.MasterSiteTimestamp
import com.akkamidd.timestamp.MasterSiteTimestamp.MasterTimestampProtocol
import org.scalatest.wordspec.AnyWordSpecLike

import java.io.{File, PrintWriter}
import scala.util.Random

class Experiment1Timestamp extends ScalaTestWithActorTestKit with AnyWordSpecLike  {
  "Experiment 1 Timestamp" must {
    "Detect Inconsistency" in {

      def randomString(length: Int) = {
        val r = new scala.util.Random
        val sb = new StringBuilder
        for (_ <- 1 to length) {
          sb.append(r.nextPrintableChar)
        }
        sb.toString
      }

      val numRuns = 10
      val numSites = 20

      val spawningActorsTimeout = 1500
      val timeoutSplit = 1500
      val timeoutMerge = 1500

      for (i <- 1 to numRuns) {
        val random: Random.type = scala.util.Random
        random.setSeed(50)

        val experimentStartMillis = System.currentTimeMillis

        val masterSite: ActorSystem[MasterTimestampProtocol] = ActorSystem(MasterSiteTimestamp(debugMode = false), "MasterSiteTimestamp")

        val listSiteNames = List.range(0, numSites).map("Site" + _.toString)
        var listFilenames = List[String]()

        var partitionList: List[Set[String]] = UtilFuncsTimestamp.spawnSites(masterSystem = masterSite, siteNameList = listSiteNames, timeout = spawningActorsTimeout)

        var thresholdSplit = 2
        var thresholdMerge = 2

        val execFileName = "output/run" + i + "_experiment1timestamp_exec.txt"
        val icdFileName = "output/run" + i + "_experiment1timestamp_icd.txt"
        val writerExec = new PrintWriter(new File(execFileName))
        val writerIcd = new PrintWriter(new File(icdFileName))

        // Can never have more merges than splits so only needs to check whether the merge threshold has been reached.
        while (thresholdMerge > 0) {
          val randomValue = random.nextInt(100) // 0 to 99

          randomValue match {
            // Upload
            case x if x <= 25 =>
              val randomSite = listSiteNames(random.nextInt(numSites))
              val time = System.currentTimeMillis().toString
              listFilenames = listFilenames :+ randomSite
              val fileName = randomString(5) + ".txt"
              UtilFuncsTimestamp.callUploadFile(randomSite, time, masterSite, fileName, partitionList)

            // Update
            case x if x > 25 && x <= 50 =>
              if (listFilenames.nonEmpty) {
                val randomSite = listSiteNames(random.nextInt(numSites))
                val randomFileIndex = random.nextInt(listFilenames.size)
                val fileName = listFilenames(randomFileIndex)
                UtilFuncsTimestamp.callUpdateFile(randomSite, fileName, masterSite, partitionList)
              }

            // Split
            case x if x > 50 && x <= 75 =>
              if (thresholdSplit != 0) {
                val randomSite = listSiteNames(random.nextInt(numSites))
                val previousPartitionList = partitionList
                partitionList = UtilFuncsTimestamp.callSplit(masterSite, partitionList, randomSite, timeoutSplit, timeoutSplit)
                if (!previousPartitionList.equals(partitionList)) {
                  thresholdSplit = thresholdSplit - 1
                }
              }

            // Merge
            case x if x > 75 && x < 100 =>
              if (thresholdMerge != 0) {
                val randomSite1 = listSiteNames(random.nextInt(numSites))
                val randomSite2 = listSiteNames(random.nextInt(numSites))
                val previousPartitionList = partitionList
                partitionList = UtilFuncsTimestamp.callMerge(randomSite1, randomSite2, masterSite, partitionList, timeoutMerge, timeoutMerge, writerIcd)
                if (!previousPartitionList.equals(partitionList)) {
                  thresholdMerge = thresholdMerge - 1
                }
              }
          }
        }

        UtilFuncsTimestamp.terminateSystem(masterSite)

        val estimatedTime = System.currentTimeMillis - experimentStartMillis
        masterSite.log.info("Experiment 2 Timestamp ended - time: " + estimatedTime.toString)

        writerExec.write(estimatedTime.toString)
        writerExec.close()
        writerIcd.close()
      }
    }
  }
}
