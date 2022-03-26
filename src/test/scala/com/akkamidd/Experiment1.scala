package com.akkamidd
import akka.actor.testkit.typed.scaladsl.{LoggingTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.ActorSystem
import com.akkamidd.UtilFuncs
import com.akkamidd.actors.MasterSite
import com.akkamidd.actors.MasterSite.{FileUpdateMasterSite, FileUploadMasterSite, MasterSiteProtocol}
import org.scalatest.wordspec.AnyWordSpecLike

class Experiment1 extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "A Greeter" must {
    "reply to greeted" in {

      val masterSite: ActorSystem[MasterSiteProtocol] = ActorSystem(MasterSite(), "MasterSite")

      var partitionList: List[Set[String]] = UtilFuncs.spawnSites(masterSite, List("A", "B", "C", "D"), List(), 1000)

      // upload files
      val time_a1 = System.currentTimeMillis().toString
      masterSite ! FileUploadMasterSite("A", time_a1, "test.txt", partitionList)

      // partitionList = UtilFuncs.callSplit(masterSite, partitionList, Set("A", "B"), 500, 500)
      partitionList = UtilFuncs.callSplit(masterSite, partitionList, "B", 500, 500)

      masterSite ! FileUpdateMasterSite("A", ("A", time_a1), partitionList)
      masterSite ! FileUpdateMasterSite("B", ("A", time_a1), partitionList)

      masterSite ! FileUpdateMasterSite("C", ("A", time_a1), partitionList)

      //  merge into {A, B, C, D}
      partitionList = UtilFuncs.callMerge("A", "C", masterSite, partitionList, Set("A", "B", "C", "D"), 500, 500)

    }
  }
}
