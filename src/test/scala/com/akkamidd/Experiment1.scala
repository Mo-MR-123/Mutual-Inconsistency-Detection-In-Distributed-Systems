package src.test.scala.com.akkamidd

import akka.actor.testkit.typed.scaladsl.{LoggingTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.ActorSystem
import com.akkamidd.UtilFuncs
import com.akkamidd.actors.MasterSite
import com.akkamidd.actors.MasterSite.{FileUploadMasterSite, MasterSiteProtocol}
import org.scalatest.wordspec.AnyWordSpecLike

class Experiment1 extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  LoggingTestKit.info("Map(A -> 0, D -> 0, B -> 0, C -> 0)").expect {
    val masterSite: ActorSystem[MasterSiteProtocol] = ActorSystem(MasterSite(), "MasterSite")

    var partitionList: List[Set[String]] = UtilFuncs.spawnSites(masterSite, List("A", "B", "C", "D"), List(), 1000)

    // upload files
    val time_a1 = System.currentTimeMillis().toString
    masterSite ! FileUploadMasterSite("A", time_a1, "test.txt", partitionList)
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


