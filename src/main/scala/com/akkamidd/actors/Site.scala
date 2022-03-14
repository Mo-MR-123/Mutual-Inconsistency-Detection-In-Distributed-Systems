package com.akkamidd.actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.akkamidd.actors.MasterSite.Broadcast
import org.slf4j.Logger

object Site {
  // SiteProtocol: top-level distinction of file messages, message types accepted for requests
  sealed trait SiteProtocol

  final case class FileUpload(timestamp: String, parent: ActorRef[MasterSite.MasterSiteProtocol], fileName: String) extends SiteProtocol
  final case class FileDeletion() extends SiteProtocol
  final case class FileUpdate(originPointer: (String, String), parent: ActorRef[MasterSite.MasterSiteProtocol]) extends SiteProtocol
  final case class FileDuplicate(originPointer: (String, String), versionVector: Map[String, Int], fileName: String, parent: ActorRef[MasterSite.MasterSiteProtocol]) extends SiteProtocol
  final case class FileUpdatedConfirm(originPointer: (String, String), updatedVersion: Int, siteActor: ActorRef[SiteProtocol]) extends SiteProtocol
  final case class printMap() extends SiteProtocol

  def apply(): Behavior[SiteProtocol] =
    fromMap(Map[(String, String), Map[String, Int]]()) // A hashmap mapping origin pointers of files to their corresponding version vectors

  private def mergeFileList(
                             fileList: Map[(String, String), Map[String, Int]],
                             originPointer: (String,String),
                             versionVector:Map[String, Int]
                           ): Map[(String, String), Map[String, Int]] =
  {
    if(!fileList.contains(originPointer)) {
      val newFileList = fileList + (originPointer -> versionVector)
      newFileList
    }
      // or if fileList already has the originPointer
    else{
      var oldVersionVector = fileList(originPointer)
      for((siteName,siteVersion) <- versionVector){
        if(!oldVersionVector.contains(siteName) || oldVersionVector(siteName)<siteVersion){
          oldVersionVector += (siteName->siteVersion)
        }
      }
      val newFileList = fileList + (originPointer -> oldVersionVector)
      newFileList
    }
  }

  private def inconcistencyDetection(
                                      log: Logger,
                                      fileListP1: Map[(String, String), Map[String, Int]],
                                      fileListP2: Map[(String, String), Map[String, Int]]
                                    ): Unit = {

    log.info(s"")
  }

  def fromMap(fileList: Map[(String, String), Map[String, Int]]): Behavior[SiteProtocol] =  Behaviors.setup { context =>
    Behaviors.receiveMessage {
      case FileUpload(timestamp, parent, fileName) =>
        val siteName = context.self.path.name

        // System wide unique ID of the file ensuring unique id for each file uploaded regardless of file name.
        // example = hash concatenation of 'A' + filename 'test' -> 10002938
        val originPointer = (siteName, timestamp)

        // Check if the file already exists by checking the hash in the originPointers map.
        // Edge case: in case two files are uploaded at the same time which result in same hash.
        if (fileList.contains(originPointer)) {
          context.log.error(s"[FileUpload] originPointer = $originPointer already exists in fileList = $fileList")
          Behaviors.same
        }

        // Version vector is a list containing what version of a file the different sites have
        // example = versionVector: (A->1, B->2)
        val versionVector = Map[String, Int](siteName -> 0)
        val newFileList = fileList +  (originPointer -> versionVector)

        parent ! Broadcast(FileDuplicate(originPointer = originPointer, versionVector = versionVector, fileName = fileName, parent = parent), context.self)

        context.log.info(s"[FileUpload] Generated file hash for site $siteName")
        context.log.info(s"[FileUpload] File uploaded! originPointer = $originPointer , fileList = $newFileList")

        fromMap(newFileList)

      case FileUpdate(originPointer: (String, String), parent) =>
        // Check if the hashFile exists
        if (fileList.contains(originPointer)) {
          // Increment the versionVector corresponding to originPointer by 1.
          val siteName = context.self.path.name

          val newVersion: Int = fileList(originPointer)(siteName) + 1
//          val newVersionMap = Map[String, Int](siteName -> newVersion)

          val newFileList = fileList.updatedWith(key = originPointer) {
            case Some(map) =>
              val newMap = map.updatedWith(key = siteName) {
                case Some(i) =>
                  Some(i + 1)

                case None =>
                  Some(0)
              }
              Some(newMap)
            case None =>
              Some(Map(siteName -> 0))
          }

          context.log.info(s"[FileUpdate] File $originPointer is updated. fileList becomes = $newFileList")
          parent ! Broadcast(FileUpdatedConfirm(originPointer = originPointer, updatedVersion = newVersion, siteActor = context.self), context.self)

          fromMap(newFileList)
        } else {
          context.log.error(s"[FileUpdate] fileHash = $originPointer does not exist in fileList = $fileList")
          fromMap(fileList)
        }

      case FileDuplicate(originPointer: (String, String), versionVector: Map[String, Int], filename: String, parent) =>
        val siteName = context.self.path.name
        // Check if site is already listed in version vector
        if (!versionVector.contains(siteName)) { // TODO: check whether to check fileList(originPointer) instead of this
          // Check if fileList actually keeps track of the file
          if (!fileList.contains(originPointer)) {
            val newVersionVector = versionVector ++ Map(siteName -> 0)
            val newFileList = fileList + (originPointer -> newVersionVector)
            context.log.info(s"[FileDuplicate] site $siteName has duplicated $originPointer using version vector $versionVector. fileList $newFileList.")
            parent ! Broadcast(FileDuplicate(originPointer = originPointer, versionVector = newVersionVector, fileName = filename, parent = parent), context.self)
            fromMap(newFileList)
          } else {
          //  val newFileList = fileList + (originPointer -> versionVector)
            val newFileList = mergeFileList(fileList,originPointer,versionVector)
            context.log.info(s"[FileDuplicate] site $siteName has version vector $versionVector. fileList $newFileList.")
            fromMap(newFileList)
          }
        } else {
          val newFileList = mergeFileList(fileList,originPointer,versionVector)
          context.log.info(s"[FileDuplicate] originPointer = $originPointer already exists in fileList = $newFileList. VersionVec $versionVector. Site $siteName")
          fromMap(newFileList)
        }

      case FileUpdatedConfirm(originPointer, newVersion, fromSite) =>
        val siteThatUpdatedVersion = fromSite.path.name
        if (fileList.contains(originPointer) && fileList(originPointer).contains(siteThatUpdatedVersion)) {
          val newFileList = fileList.updatedWith(key = originPointer) {
            case Some(map) =>
              val newMap = map.updatedWith(key = siteThatUpdatedVersion) {
                case Some(_) =>
                  Some(newVersion)

                case None =>
                  Some(-1)
              }
              Some(newMap)
            case None =>
              Some(Map(siteThatUpdatedVersion -> -1))
          }

          context.log.info(s"[FileUpdatedConfirm] originPointer = $originPointer, version = $newVersion, newFileList = $newFileList. Site ${context.self.path.name}")
          fromMap(newFileList)
        } else {
          context.log.error(s"originPointer = $originPointer not in fileList = $fileList. newVersion $newVersion. Site ${context.self.path.name}")
          Behaviors.unhandled
        }
      case printMap() =>
        context.log.info(s"I print fileList = $fileList.")
        fromMap(fileList)

      case _ =>
        Behaviors.empty
    }
  }

}
