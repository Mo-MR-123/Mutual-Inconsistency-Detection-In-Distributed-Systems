package com.akkamidd.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.akkamidd.actors.MasterSite.Broadcast

import scala.collection.{immutable, mutable}
import scala.util.hashing.MurmurHash3


object Site {
  // SiteProtocol: top-level distinction of file messages
  sealed trait SiteProtocol

  // FileMessagesReq: message types accepted for requests
  sealed trait FileMessagesReq extends SiteProtocol

  final case class FileUpload(siteID: ActorRef[Site.SiteProtocol], timestamp: String, parent: ActorRef[MasterSite.MasterSiteProtocol], fileName: String) extends FileMessagesReq

  final case class FileDeletion() extends FileMessagesReq

  final case class FileUpdate(originPointer: (String, String), siteID: ActorRef[Site.SiteProtocol]) extends FileMessagesReq

  final case class FileDuplicate(originPointer: (String, String), versionVector: immutable.Map[String, Int], fileName: String) extends FileMessagesReq

  // FileMessagesResp: file messages accepted for response
  sealed trait FileMessagesResp extends SiteProtocol

  final case class FileUpdatedConfirm(siteName: String) extends FileMessagesResp

  // A hashmap mapping origin pointers of files to their corresponding version vectors
  var fileList: mutable.Map[(String, String), immutable.Map[String, Int]] = mutable.Map[(String, String), immutable.Map[String, Int]]()

  def apply(): Behavior[SiteProtocol] = Behaviors.setup { context =>

    // TODO: setup subscription to publisher

    Behaviors.receiveMessage {
      case FileUpload(siteID, timestamp, parent, fileName) =>
        val siteName = siteID.path.name

        // System wide unique ID of the file ensuring unique id for each file uploaded regardless of file name.
        // example = hash concatenation of 'A' + filename 'test' -> 10002938
        val originPointer = (siteName, timestamp)

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

        parent ! Broadcast(FileDuplicate(originPointer = originPointer, versionVector = versionVector, fileName = fileName), context.self)

        context.log.info(s"Generated file hash for site $siteID")
        context.log.info(s"File uploaded! originPointer = $originPointer , fileList = $fileList")

        Behaviors.same

      case FileUpdate(originPointer: (String, String), siteID: ActorRef[Site.SiteProtocol]) =>
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

      case FileDuplicate(originPointer: (String, String), versionVector: immutable.Map[String, Int], filename: String) =>
        val siteName = context.self.path.name
        // Check if site is already listed in version vector
        if (versionVector.contains(siteName)) {
          // Check if fileList actually keeps track of the file
          if (!fileList.contains(originPointer)) {
            val newVersionVector = versionVector ++ Map(siteName -> 0)
            fileList += ((originPointer._1, originPointer._2) -> newVersionVector)
            context.log.info(s"site $siteName has duplicated $originPointer using version vector $versionVector. Filename $filename")
          } else {
            fileList += ((originPointer._1, originPointer._2) -> versionVector)
          }
          Behaviors.same
        } else {
          context.log.info(s"originPointer = $originPointer already exists in fileList = $fileList")
          Behaviors.unhandled
        }

      case _ =>
        Behaviors.unhandled
    }
  }
}
