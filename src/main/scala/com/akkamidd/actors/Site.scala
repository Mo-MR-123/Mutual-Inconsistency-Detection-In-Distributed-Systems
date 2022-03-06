package com.akkamidd.actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import scala.collection.immutable
import scala.util.hashing.MurmurHash3

object Site {
  // SiteProtocol: top-level distinction of file messages
  sealed trait SiteProtocol

  // FileMessagesReq: message types accepted for requests
  sealed trait FileMessagesReq extends SiteProtocol
  final case class FileUpload(site: String, timestamp: String) extends FileMessagesReq
  final case class FileDeletion() extends FileMessagesReq
  final case class FileUpdate(hashFile: String, version: Int) extends FileMessagesReq

  // FileMessagesResp: file messages accepted for response
  sealed trait FileMessagesResp extends SiteProtocol
  final case class FileUpdatedConfirm(siteName:String) extends FileMessagesResp

  // A hashmap mapping origin pointers of files to their correponding version vectors
  val fileList: immutable.Map[String, immutable.Map[String, Int]] = Map[String, immutable.Map[String, Int]]()

  def apply(): Behavior[SiteProtocol] = Behaviors.receive {
    case (context, FileUpload(siteID, timestamp)) =>
      // System wide unique ID of the file ensuring unique id for each file uploaded regardless of file name.
      // example = hash concatenation of 'A' + filename 'test' -> 10002938
      val originPointer: String = MurmurHash3.stringHash(siteID.concat(timestamp)).toString

      // Check if the file already exists by checking the hash in the originPointers map.
      // Edge case: in case two files are uploaded at the same time which result in same hash.
      if (fileList.contains(originPointer)) {
        context.log.info(s"originPointer = $originPointer already exists in fileList = $fileList")
        Behaviors.unhandled
      }

      // Version vector is a list containing what version of a file the different sites have
      // example = versionVector: (A->1, B->2)
      val versionVector: immutable.Map[String, Int] = Map[String, Int](siteID, 0)

      context.log.info(s"Generated file hash for site $siteID")
      context.log.info(s"File $siteID is uploaded! Element of version vector = $versionVector")

      Behaviors.same

    case (context, FileUpdate(originPointer: String, version: Int)) =>
      // Check if the hashFile exists
      if (fileList.contains(originPointer)) {
        // Increment the versionVector corresponding to originPointer by 1.
        fileList ++ (originPointer, fileList(originPointer) + 1)
        context.log.info(s"File $originPointer is requested to be updated. vv becomes = $fileList(originPointer)")
        Behaviors.same
      } else {
        context.log.info(s"fileHash = $originPointer does not exist in versionVector = $fileList(originPointer)")
        Behaviors.unhandled
      }

    case (c, m) =>
      c.log.info(m.toString)
      Behaviors.unhandled
  }
}
