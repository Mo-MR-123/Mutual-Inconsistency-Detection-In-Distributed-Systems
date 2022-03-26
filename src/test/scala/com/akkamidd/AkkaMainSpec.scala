package src.test.scala.com.akkamidd

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorSystem
import com.akkamidd.AkkaMain.{masterSite, partitionList, time_a1}
import com.akkamidd.UtilFuncs
import com.akkamidd.actors.MasterSite
import com.akkamidd.actors.MasterSite.{FileUpdateMasterSite, FileUploadMasterSite, MasterSiteProtocol}
import org.scalatest.wordspec.AnyWordSpecLike

class AkkaMainSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
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
/*
 def main(args: Array[String]): Unit = {


    print("Select one:" +
      "\n1: Experiment: Write X Bytes " +
      "\n2: Experiment: Write X Keys (with value of 1 Byte) in Y batches" +
      "\n3: Experiment: Write X Bytes per Y Keys (even/random distributed)" +
      "\n4: Experiment: gGet cost with(out) replica" +
      "\n5: Experiment: Deliver cost" +
      "\n6: Experiment: Static Baselines" +
      "\n7: Experiment: gGet cost with(out) replica, specific range" +
      "\nOption: ")
  
  }
}
*/


