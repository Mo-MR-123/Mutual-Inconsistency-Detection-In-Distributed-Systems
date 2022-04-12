package com.akkamidd
import scala.io.Source

object ResultsFormat {

  def intCount(filename: String): Int = {
    val count = Source.fromFile(filename).getLines.map {_.toInt}.sum
    count
  }

  def printVersionVector(numberOfExperiments: Int, numberOfRuns: Int): Unit = {
    for (j <- 1 to numberOfExperiments) {
      print("Experiment" + j + ":\n")
      var icdSum: Int = 0
      var execSum: Int = 0

      for (i <- 1 to numberOfRuns) {
        val icdCount = intCount("output/run" + i + "_experiment" + j + "_icd.txt")
        val execCount = intCount("output/run" + i + "_experiment" + j + "_exec.txt")
        icdSum += icdCount
        execSum += execCount
        print(s"Run $i - Execution time: $execCount, icd count: $icdCount \n")
      }

      val icdAverage = icdSum / numberOfRuns
      val execAverage = execSum / numberOfRuns
      print(s"Experiment $j - Average execution time: $execAverage, icd average count: $icdAverage \n")
    }
  }

  def printTimestamp(numberOfExperiments: Int, numberOfRuns: Int): Unit = {
    for (j <- 1 to numberOfExperiments) {
      print("Experiment" + j + "Timestamp:\n")
      var icdSum: Int = 0
      var execSum: Int = 0

      for (i <- 1 to numberOfRuns) {
        val icdCount = intCount("output/run" + i + "_experiment" + j + "timestamp_icd.txt")
        val execCount = intCount("output/run" + i + "_experiment" + j + "timestamp_exec.txt")
        icdSum += icdCount
        execSum += execCount
        print(s"Run $i - Execution time: $execCount, icd count: $icdCount \n")
      }

      val icdAverage = icdSum / numberOfRuns
      val execAverage = execSum / numberOfRuns
      print(s"Experiment $j Timestamp - Average execution time: $execAverage, icd average count: $icdAverage \n")
    }
  }

  def main(args: Array[String]): Unit = {
    val numberOfExperiments = 1
    val numberOfRuns = 10

    printVersionVector(numberOfExperiments, numberOfRuns)
    printTimestamp(numberOfExperiments, numberOfRuns)
  }
}
