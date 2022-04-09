package com.akkamidd
import scala.io.Source

object ResultsFormat {

  def readFile(filename: String) = {
    val icdCount = Source.fromFile(filename).getLines.map{ _.toInt
    }.foldLeft(0)(_+_)
    print(icdCount)
  }

  def main(args: Array[String]) {
    readFile("output/run1_experiment1_icd.txt")

//    val exec = Source.fromFile("output/run1_experiment1_exec.txt").mkString
//    val icd = Source.fromFile("output/run1_experiment1_icd.txt")
//
//    print(exec + "\n")
//
//    var icdCount: Int = 0
//    for(line <- icd) {
//      print(line.toInt + "\n")
//      icdCount += line.asDigit
//    }
//    print(icdCount)
  }
}
