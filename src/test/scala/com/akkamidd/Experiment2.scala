package com.akkamidd

class Experiment2 {
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
