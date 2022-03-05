package com.akkamidd.actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import scala.collection.mutable
import scala.util.hashing.MurmurHash3

object Site {
  sealed trait FileMessages
  final case class FileUpload(siteID: String, fileName: String) extends FileMessages
  final case class FileDeletion() extends FileMessages
  final case class FileUpdate(hashFile: String, version: Int) extends FileMessages
  final case class FileUpdatedConfirm(siteName:String) extends FileMessages

  val versionVector: mutable.Map[String, Int] = mutable.Map[String, Int]() // eg versionVector: A->1, B->2
  val originPointers: mutable.Map[String, String] = mutable.Map[String, String]() //

  def apply(sideID: String): Behavior[FileMessages] = Behaviors.receive {
    case (context, FileUpload(siteID, fileName)) =>
      // Concat siteID with fileName and hash the result.
      // This is done to ensure unique id for each file uploaded.
      val fileHash: String = MurmurHash3.stringHash(siteID.concat(fileName)).toString

      // Check if the file already exists by checking the hash in the originPointers map.
      // Two options possible here:
      //    1: Overwrite/Reset the existing value of fileHash in versionVector and originPointer.
      //    2: Ignore this message and don't do anything.
      // For now the second option is done. TODO: check whether to use 1st option instead
      if (originPointers.contains(fileHash)) {
        context.log.info(s"fileHash = $fileHash already exists in originPointers = $originPointers")
        Behaviors.unhandled
      }

      originPointers.put(fileHash, fileName)
      versionVector.put(fileHash, 0)

      context.log.info(s"Generated file hash for site $siteID")
      context.log.info(s"File $siteID is uploaded! Element of version vector = $versionVector")

      Behaviors.same

    case (context, FileUpdate(hashFile: String, version: Int)) =>
      // Check if the hashFile exists
      if (versionVector.contains(hashFile)) {
        // Increment the versionVector corresponding to hashFile by 1.
        versionVector.put(hashFile, versionVector(hashFile) + 1)
        context.log.info(s"File $hashFile is requested to be updated. vv becomes = $versionVector")
        Behaviors.same
      } else {
        context.log.info(s"fileHash = $hashFile does not exist in versionVector = $versionVector")
        Behaviors.unhandled
      }

    case _ =>
      Behaviors.unhandled
  }
}
