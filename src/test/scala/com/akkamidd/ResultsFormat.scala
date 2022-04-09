package com.akkamidd
import scala.io.Source

object ResultsFormat {

  def intCount(filename: String): Int = {
    val count = Source.fromFile(filename).getLines.map{ _.toInt
    }.foldLeft(0)(_+_)
    count
  }

  def main(args: Array[String]) {
    val numberOfExperiments = 1
    val numberOfRuns = 5

    for (j <- 1 to numberOfExperiments) {
      print("Experiment" + j + ":\n")
      var icdSum: Int = 0
      var execSum: Int = 0

      for (i <- 1 to numberOfRuns) {
        val icdCount = intCount("output/run" + i + "_experiment" + j + "_icd.txt")
        val execCount = intCount("output/run" + i + "_experiment" + j + "_exec.txt")
        icdSum += icdCount
        execSum += execCount
        print(s"Run ${i} - Execution time: ${execCount}, icd count: ${icdCount} \n")
      }

      val icdAverage = icdSum / numberOfRuns
      val execAverage = execSum / numberOfRuns
      print(s"Experiment ${j} - Average execution time: ${execAverage}, icd average count: ${icdAverage} \n")
    }
  }
}
