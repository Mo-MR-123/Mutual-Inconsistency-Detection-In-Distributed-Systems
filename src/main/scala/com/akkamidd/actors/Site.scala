package com.akkamidd.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import scala.collection.{immutable, mutable}
import scala.util.hashing.MurmurHash3

object Site {
  // SiteProtocol: top-level distinction of file messages
  sealed trait SiteProtocol

  // FileMessagesReq: message types accepted for requests
  sealed trait FileMessagesReq extends SiteProtocol
  final case class FileUpload(siteID: ActorRef[Site.SiteProtocol], timestamp: String) extends FileMessagesReq
  final case class FileDeletion() extends FileMessagesReq
  final case class FileUpdate(originPointer: Int, siteID: ActorRef[Site.SiteProtocol]) extends FileMessagesReq

  // FileMessagesResp: file messages accepted for response
  sealed trait FileMessagesResp extends SiteProtocol
  final case class FileUpdatedConfirm(siteName: String) extends FileMessagesResp

  // A hashmap mapping origin pointers of files to their corresponding version vectors
  var fileList: mutable.Map[Int, immutable.Map[String, Int]] = mutable.Map[Int, immutable.Map[String, Int]]()

  def apply(): Behavior[SiteProtocol] = Behaviors.receive {
    case (context, FileUpload(siteID, timestamp)) =>
      val siteName = siteID.path.name

      // System wide unique ID of the file ensuring unique id for each file uploaded regardless of file name.
      // example = hash concatenation of 'A' + filename 'test' -> 10002938
      val originPointer: Int = MurmurHash3.stringHash(siteName.concat(timestamp))

      // Check if the file already exists by checking the hash in the originPointers map.
      // Edge case: in case two files are uploaded at the same time which result in same hash.
      if (fileList.contains(originPointer)) {
        context.log.info(s"originPointer = $originPointer already exists in fileList = $fileList")
        Behaviors.unhandled
      }

      // Version vector is a list containing what version of a file the different sites have
      // example = versionVector: (A->1, B->2)
      val versionVector = Map[String, Int](siteName -> 0)
      fileList += (originPointer -> versionVector)

      context.log.info(s"Generated file hash for site $siteID")
      context.log.info(s"File uploaded! originPointer = $originPointer , fileList = $fileList")

      Behaviors.same

    case (context, FileUpdate(originPointer: Int, siteID: ActorRef[Site.SiteProtocol])) =>
      // Check if the hashFile exists
      if (fileList.contains(originPointer)) {
        // Increment the versionVector corresponding to originPointer by 1.
        val siteName = siteID.path.name

        val newVersion: Int = fileList(originPointer)(siteName) + 1
        val newVersionMap = Map[String, Int](siteName -> newVersion)
        fileList(originPointer) = newVersionMap

        context.log.info(s"File $originPointer is updated. fileList becomes = $fileList")
        Behaviors.same
      } else {
        context.log.info(s"fileHash = $originPointer does not exist in fileList = $fileList")
        Behaviors.unhandled
      }

    case (c, m) =>
      c.log.info(m.toString)
      Behaviors.unhandled
  }
}
