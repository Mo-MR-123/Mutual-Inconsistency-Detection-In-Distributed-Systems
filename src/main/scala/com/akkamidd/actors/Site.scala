package com.akkamidd.actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import scala.collection.mutable

object Site {
  sealed trait FileMessages
  final case class FileUpload(siteName:String) extends FileMessages
  final case class FileDeletion() extends FileMessages
  final case class FileUpdate(hashFile: Int, siteID: String, version: Int) extends FileMessages
  final case class FileUpdatedConfirm(siteName:String) extends FileMessages

  val versionVector: mutable.Map[String, Int] = mutable.Map[String, Int]() // eg versionVector: A->1, B->2

  def apply(sideID: String): Behavior[FileMessages] = Behaviors.receive {
    case (context, FileUpload(siteName)) =>
      context.log.info("", siteName)
      versionVector.put(siteName,0)
      context.log.info("File {} is uploaded! Element of version vector = {}",siteName,versionVector)
      Behaviors.same

    case (context, FileUpdate(hashFile: Int, siteID: String, version: Int)) =>
      versionVector.put(context.self.path.name,versionVector(context.self.path.name)+1)
      context.log.info("File {} is requested to be updated. vv becomes ={} ", context.self.path.name,versionVector)
      // context.log.info(s"Element of version vector = $versionVector")
      Behaviors.same
  }
}
