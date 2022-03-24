package com.akkamidd.actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import com.akkamidd.actors.MasterSite.Broadcast
import org.slf4j.Logger

import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success

object Site {
  // SiteProtocol: The messages that define the protocol between Sites
  sealed trait SiteProtocol

  final case class FileUpload(
                               timestamp: String,
                               parent: ActorRef[MasterSite.MasterSiteProtocol],
                               fileName: String,
                               partitionSet: Set[ActorRef[SiteProtocol]]
                             ) extends SiteProtocol
//  final case class FileDeletion(replyTo: ActorRef[SiteProtocol]) extends SiteProtocol
  final case class FileUpdate(
                               originPointer: (String, String),
                               parent: ActorRef[MasterSite.MasterSiteProtocol],
                               partitionSet: Set[ActorRef[SiteProtocol]]
                             ) extends SiteProtocol
  final case class FileDuplicate(
                                  originPointer: (String, String),
                                  versionVector: Map[String, Int],
                                  fileName: String,
                                  parent: ActorRef[MasterSite.MasterSiteProtocol],
                                  partitionSet: Set[ActorRef[SiteProtocol]]
                                ) extends SiteProtocol
  final case class FileUpdatedConfirm(
                                       originPointer: (String, String),
                                       updatedVersion: Int,
                                       siteActor: ActorRef[SiteProtocol]
                                     ) extends SiteProtocol
  final case class Merged(
                           to: ActorRef[SiteProtocol],
                           parent: ActorRef[MasterSite.MasterSiteProtocol],
                           partitionSet: Set[ActorRef[SiteProtocol]]
                         ) extends SiteProtocol
  final case class CheckInconsistency(
                           fileList: Map[(String, String), Map[String, Int]],
                           parent: ActorRef[MasterSite.MasterSiteProtocol],
                           partitionSet: Set[ActorRef[SiteProtocol]]
                         ) extends SiteProtocol
  final case class ReplaceFileList(
                                    fileListToReplace: Map[(String, String), Map[String, Int]]
                                  ) extends SiteProtocol
  final case class BroadcastDone(msg: String) extends SiteProtocol

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
    else { // or if fileList already has the originPointer
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

  private def updateFileList(
                      fileList: Map[(String, String), Map[String, Int]],
                      originPointer: (String, String),
                      siteName: String,
                      newVal: Int
                    ): Map[(String, String), Map[String, Int]] = {
    fileList.updatedWith(key = originPointer) {
      case Some(map) =>
        val newMap = map.updatedWith(key = siteName) {
          case Some(_) =>
            Some(newVal)

          case None =>
            Some(-1)
        }
        Some(newMap)
      case None =>
        Some(Map("" -> -1))
    }
  }

  /**
   * Helper method for merging two file lists
   * @param log Logger for printing out information to the terminal.
   * @param fileListP1 File list of Partition 1.
   * @param fileListP2 File List of Partition 2.
   * @return Merged file List.
   */
  private def inconcistencyDetection(
                                      log: Logger,
                                      fileListP1: Map[(String, String), Map[String, Int]],
                                      fileListP2: Map[(String, String), Map[String, Int]]
                                    ): Map[(String, String), Map[String, Int]] = {
    // Assume both lists are same format
    val zippedLists = (fileListP1 zip fileListP2).map(pair => (pair._1._1, pair._1._2, pair._2._2))
    var fileList = Map[(String, String), Map[String, Int]]()

    for ((originPointer, vv1, vv2) <- zippedLists) {
      val zipVV = vv1 zip vv2
      var versionVector = Map[String, Int]()

      // Keep track on the differences with regards to the version vector for each partition respective.
      var count1 = 0
      var count2 = 0

      for (((siteName, version1), (_, version2)) <- zipVV) {
        if (version1 > version2) {
          count1 += 1
          versionVector = versionVector + (siteName -> version1)
        } else if (version1 < version2) {
          count2 += 1
          versionVector = versionVector + (siteName -> version2)
        } else {
          versionVector = versionVector + (siteName -> version1) // doesn't matter which version we take.
        }
      }

      // Check whether one of the version vectors is dominant over the other or if both contain conflicting updated site versions.
      if (count1 != 0 && count2 == 0 || count2 != 0 && count1 == 0) {
        log.info(s"For File $originPointer -> Compatible version conflict detected: $vv1 - $vv2")
      } else if (count1 != 0 && count2 != 0) {
        log.info(s"For File $originPointer -> Incompatible version conflict detected: $vv1 - $vv2")
      } else {
        log.info(s"For File $originPointer -> no version conflict detected: $vv1 - $vv2")
      }
      fileList = fileList + (originPointer -> versionVector)
    }
    log.info(s"[LOGGER ID] $fileList. FL1 $fileListP1  FL2 $fileListP2")
    fileList
  }

  def fromMap(fileList: Map[(String, String), Map[String, Int]]): Behavior[SiteProtocol] =  Behaviors.setup {
    // asking someone requires a timeout, if the timeout hits without response
    // the ask is failed with a TimeoutException
    implicit val timeout: Timeout = 2.seconds

    context =>
      Behaviors.receiveMessage {
        case FileUpload(timestamp, parent, fileName, partitionSet) =>
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
          val newFileList = fileList + (originPointer -> versionVector)

          parent ! Broadcast(
            FileDuplicate(originPointer = originPointer, versionVector = versionVector, fileName = fileName, parent = parent, partitionSet),
            context.self,
            partitionSet
          )

          context.log.info(s"[FileUpload] Generated file hash for site $siteName")
          context.log.info(s"[FileUpload] File uploaded! originPointer = $originPointer , fileList = $newFileList")

          fromMap(newFileList)

        case FileUpdate(originPointer: (String, String), parent, partitionList) =>
          // Check if the hashFile exists
          if (fileList.contains(originPointer)) {
            val siteName = context.self.path.name

            // Increment the versionVector corresponding to originPointer by 1.
            val newVersion: Int = fileList(originPointer)(siteName) + 1
            val newFileList = updateFileList(fileList, originPointer, siteName, newVersion)

            context.log.info(s"[FileUpdate] File $originPointer is updated. fileList becomes = $newFileList")
            parent ! Broadcast(
              FileUpdatedConfirm(originPointer = originPointer, updatedVersion = newVersion, siteActor = context.self),
              context.self,
              partitionList
            )

            fromMap(newFileList)
          } else {
            context.log.error(s"[FileUpdate] fileHash = $originPointer does not exist in fileList = $fileList")
            fromMap(fileList)
          }

        case FileDuplicate(originPointer: (String, String), versionVector: Map[String, Int], filename: String, parent, partitionSet) =>
          val siteName = context.self.path.name
          // Check if site is already listed in version vector
          if (!versionVector.contains(siteName)) {
            // Check if fileList actually keeps track of the file
            if (!fileList.contains(originPointer)) {
              val newVersionVector = versionVector ++ Map(siteName -> 0)
              val newFileList = fileList + (originPointer -> newVersionVector)
              context.log.info(s"[FileDuplicate] site $siteName has duplicated $originPointer using version vector $versionVector. fileList $newFileList.")

              parent ! Broadcast(
                FileDuplicate(originPointer = originPointer, versionVector = newVersionVector, fileName = filename, parent = parent, partitionSet),
                context.self,
                partitionSet
              )

              fromMap(newFileList)
            } else {
              val newFileList = mergeFileList(fileList, originPointer, versionVector)
              context.log.info(s"[FileDuplicate] site $siteName has version vector $versionVector. fileList $newFileList.")
              fromMap(newFileList)
            }
          } else {
            val newFileList = mergeFileList(fileList, originPointer, versionVector)
            context.log.info(s"[FileDuplicate] originPointer = $originPointer already exists in fileList = $newFileList. VersionVec $versionVector. Site $siteName")
            fromMap(newFileList)
          }

        case FileUpdatedConfirm(originPointer, newVersion, fromSite) =>
          val siteThatUpdatedVersion = fromSite.path.name
          if (fileList.contains(originPointer) && fileList(originPointer).contains(siteThatUpdatedVersion)) {
            val newFileList = updateFileList(fileList, originPointer, siteThatUpdatedVersion, newVersion)

            context.log.info(s"[FileUpdatedConfirm] originPointer = $originPointer, version = $newVersion, newFileList = $newFileList. Site ${context.self.path.name}")
            fromMap(newFileList)
          } else {
            context.log.error(s"originPointer = $originPointer not in fileList = $fileList. newVersion $newVersion. Site ${context.self.path.name}")
            Behaviors.unhandled
          }

        case Merged(to, parent, partitionSet) =>
          context.log.info(s"[Merged] sending fileList of site ${context.self.path.name} to site ${to.path.name}. FileList sent: $fileList")
          to ! CheckInconsistency(fileList, parent, partitionSet)
          fromMap(fileList)

        case CheckInconsistency(fromFileList, parent, partitionSet) =>
          val newFileList = inconcistencyDetection(context.log, fileList, fromFileList)
          parent ! Broadcast(
            ReplaceFileList(newFileList),
            context.self,
            partitionSet
          )
          fromMap(newFileList)

        case ReplaceFileList(newFileList) =>
          context.log.info(s"[ReplaceFileList.${context.self.path.name}] Replaced FileList with newFileList $newFileList")
          fromMap(newFileList)

        case BroadcastDone(msg: String) =>
          context.log.info(s"$msg")
          Behaviors.same

      }
  }

}
