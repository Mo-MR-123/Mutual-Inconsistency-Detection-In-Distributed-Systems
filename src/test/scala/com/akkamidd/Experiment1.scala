package com.akkamidd
import akka.actor.testkit.typed.scaladsl.{LoggingTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.ActorSystem
import com.akkamidd.UtilFuncs
import com.akkamidd.actors.MasterSite
import com.akkamidd.actors.MasterSite.{FileUpdateMasterSite, FileUploadMasterSite, MasterSiteProtocol}
import org.scalatest.wordspec.AnyWordSpecLike

class Experiment1 extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "Experiment 1" must {
    "Detect Inconsistency" in {
      val spawningActorsTimeout = 1000
      val timeoutSplit = 500
      val timeoutMerge = 500

      val experimentStartMillis = System.currentTimeMillis()

      val masterSite: ActorSystem[MasterSiteProtocol] = ActorSystem(MasterSite(false), "MasterSite")

      var partitionList: List[Set[String]] = UtilFuncs.spawnSites(masterSite, List("A", "B", "C", "D"), spawningActorsTimeout)

      // upload files
      val time_a1 = System.currentTimeMillis().toString

      UtilFuncs.callUploadFile("A", time_a1, masterSite, "test.txt", partitionList)

      // partitionList = UtilFuncs.callSplit(masterSite, partitionList, Set("A", "B"), 500, 500)
      partitionList = UtilFuncs.callSplit(masterSite, partitionList, "B", timeoutSplit, timeoutSplit)

      UtilFuncs.callUpdateFile("A", ("A", time_a1), masterSite, partitionList)
      UtilFuncs.callUpdateFile("B", ("A", time_a1), masterSite, partitionList)
      UtilFuncs.callUpdateFile("C", ("A", time_a1), masterSite, partitionList)

      //  merge into {A, B, C, D}
      partitionList = UtilFuncs.callMerge("A", "C", masterSite, partitionList, timeoutMerge, timeoutMerge)

//      val experimentEndMillis = System.currentTimeMillis() - timeoutMerge*2 - timeoutSplit*2 - spawningActorsTimeout
      val experimentEndMillis = System.currentTimeMillis()

      println(s"[Experiment 1] Execution time in millis: ${experimentEndMillis - experimentStartMillis}")
    }
  }
}
