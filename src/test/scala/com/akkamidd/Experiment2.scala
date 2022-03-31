package com.akkamidd

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorSystem
import com.akkamidd.actors.MasterSite
import com.akkamidd.actors.MasterSite.MasterSiteProtocol
import org.scalatest.wordspec.AnyWordSpecLike

import java.io.{File, PrintWriter}

class Experiment2 extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "Experiment 2" must {
   "Detect Inconsistency" in {

     def randomString(length: Int) = {
       val r = new scala.util.Random
       val sb = new StringBuilder
       for (_ <- 1 to length) {
         sb.append(r.nextPrintableChar)
       }
       sb.toString
     }
     val numRun = 10

     for (i <- 1 to numRun) {

       val spawningActorsTimeout = 1000
       val timeoutSplit = 1000
       val timeoutMerge = 1000

       val experimentStartMillis = System.currentTimeMillis
       val masterSite: ActorSystem[MasterSiteProtocol] = ActorSystem(MasterSite(debugMode = false), "MasterSite")
       val numSites = 50
       val listSiteNames = List.range(0, numSites).map("Site" + _.toString)
       var listOriginPointers = Map[String, String]()

       var partitionList: List[Set[String]] = UtilFuncs.spawnSites(masterSystem = masterSite, siteNameList = listSiteNames, timeout = spawningActorsTimeout)

       var thresholdSplit = 2
       var thresholdMerge = 2
       val execFileName = "output/run" + i + "_experiment2_exec.txt"
       val icdFileName = "output/run" + i + "_experiment2_icd.txt"
       val writerExec = new PrintWriter(new File(execFileName ))
       val writerIcd = new PrintWriter(new File(icdFileName ))

       val random = scala.util.Random
       // Can never have more merges than splits so only needs to check whether the merge threshold has been reached.
       while (thresholdMerge > 0) {
         val randomValue = random.nextInt(100) // 0 to 100

         randomValue match {
           // Upload
           case x if x <= 25 =>
             val randomSite = listSiteNames(random.nextInt(numSites))
             val time = System.currentTimeMillis().toString
             listOriginPointers = listOriginPointers + (randomSite -> time)
             val fileName = randomString(5) + ".txt"
             UtilFuncs.callUploadFile(randomSite, time, masterSite, fileName, partitionList)

           // Update
           case x if x > 25 && x <= 50 =>
             if(listOriginPointers.size>0) {
               val randomSite = listSiteNames(random.nextInt(numSites))
               val randomFileIndex = random.nextInt(listOriginPointers.size)
               val tuple = listOriginPointers.toList(randomFileIndex)
               UtilFuncs.callUpdateFile(randomSite, tuple, masterSite, partitionList)
             }

           // Split
           case x if x > 50 && x <= 75 =>
             if (thresholdSplit != 0) {
               val randomSite = listSiteNames(random.nextInt(numSites))
               val previousPartitionList = partitionList
               partitionList = UtilFuncs.callSplit(masterSite, partitionList, randomSite, timeoutSplit, timeoutSplit)
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
               partitionList = UtilFuncs.callMerge(randomSite1, randomSite2, masterSite, partitionList, timeoutMerge, timeoutMerge, writerIcd)
               if (!previousPartitionList.equals(partitionList)) {
                 thresholdMerge = thresholdMerge - 1
               }
             }

         }
       }


       val estimatedTime = System.currentTimeMillis - experimentStartMillis
       masterSite.log.info("Experiment 2 ended - time: " + estimatedTime.toString)
       writerExec.write((estimatedTime).toString)
       writerExec.close()
       writerIcd.close()

     }
   }
  }
  //  val masterSiteTimestamp: ActorSystem[TimestampProtocol] = ActorSystem(MasterSiteTimestamp(), "MasterSiteTimestamp")
  //
  //    var partitionList: List[Set[String]] = spawnSites(masterSiteTimestamp, List("A", "B", "C", "D"), List(), 1000)
  //
  //    val time_a1 = System.currentTimeMillis().toString
  //    masterSiteTimestamp ! MasterSiteTimestamp.FileUploadMasterSite("A", time_a1, "test.txt", partitionList)
  //    masterSiteTimestamp ! MasterSiteTimestamp.FileUploadMasterSite("A", time_a1, "test2.txt", partitionList)
  //    // split into {A,B} {C,D}
  //    partitionList = callSplit(masterSiteTimestamp, partitionList, Set("A", "B"), 500, 500)
  //
  //    val time_a2 = System.currentTimeMillis().toString
  //    masterSiteTimestamp ! MasterSiteTimestamp.FileUpdateMasterSite("A", "test.txt", time_a2, partitionList)
  //    masterSiteTimestamp ! MasterSiteTimestamp.FileUpdateMasterSite("B", "test.txt", time_a2, partitionList)
  //    masterSiteTimestamp ! MasterSiteTimestamp.FileUpdateMasterSite("C", "test.txt", time_a2, partitionList)
  //
  //    //  merge into {A, B, C, D}
  //    partitionList = callMerge("A", "C", masterSiteTimestamp, partitionList, Set("A", "B", "C", "D"), 500, 500)
}
