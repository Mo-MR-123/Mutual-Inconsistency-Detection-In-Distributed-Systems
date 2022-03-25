package com.akkamidd.timestamp

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import com.akkamidd.timestamp.MasterSite.Broadcast
import org.slf4j.Logger

import scala.concurrent.duration.DurationInt

object Site {
  // SiteProtocol: The messages that define the protocol between Sites
  sealed trait SiteProtocol

  final case class FileUpload(
                               fileName: String,
                               timestamp: String,
                               parent: ActorRef[MasterSite.MasterSiteProtocol],
                               partitionSet: Set[ActorRef[SiteProtocol]]
                             ) extends SiteProtocol
  //  final case class FileDeletion(replyTo: ActorRef[SiteProtocol]) extends SiteProtocol
  final case class FileUpdate(
                               fileName: String,
                               newTimestamp: String,
                               parent: ActorRef[MasterSite.MasterSiteProtocol],
                               partitionSet: Set[ActorRef[SiteProtocol]]
                             ) extends SiteProtocol
  final case class FileDuplicate(
                                  fileName: String,
                                  timestamp: String,
                                  parent: ActorRef[MasterSite.MasterSiteProtocol],
                                  partitionSet: Set[ActorRef[SiteProtocol]]
                                ) extends SiteProtocol
  final case class FileUpdatedConfirm(
                                       fileName: String,
                                       updatedTimestamp: String,
                                       siteActor: ActorRef[SiteProtocol]
                                     ) extends SiteProtocol
  final case class Merged(
                           to: ActorRef[SiteProtocol],
                           parent: ActorRef[MasterSite.MasterSiteProtocol],
                           partitionSet: Set[ActorRef[SiteProtocol]]
                         ) extends SiteProtocol
  final case class CheckInconsistency(
                                       fileList: Map[String, String],
                                       parent: ActorRef[MasterSite.MasterSiteProtocol],
                                       partitionSet: Set[ActorRef[SiteProtocol]]
                                     ) extends SiteProtocol
  final case class ReplaceFileList(
                                    fileListToReplace: Map[String, String]
                                  ) extends SiteProtocol
  final case class BroadcastDone(msg: String) extends SiteProtocol


  
  def apply(): Behavior[SiteProtocol] =
    // A hashmap mapping filename to the timestamp
    fromMap(Map[String, String]())

  def fromMap(fileList: Map[String, String]): Behavior[SiteProtocol] =  Behaviors.setup {
    // asking someone requires a timeout, if the timeout hits without response
    // the ask is failed with a TimeoutException
    implicit val timeout: Timeout = 2.seconds

    context =>
      Behaviors.receiveMessage {

        /**
         * Upload file onto the current site. A new entry is added to the filelist and sends to other sites to duplicate.
         */
        case FileUpload(fileName: String, timestamp: String, parent: ActorRef[MasterSite.MasterSiteProtocol], partitionSet: Set[ActorRef[SiteProtocol]]) =>
          // Check if the file already exists in the filelist.
          if (fileList.contains(fileName)) {
            context.log.error(s"[FileUpload] File name = $fileName already exists in fileList = $fileList")
            Behaviors.same
          }

          // Append new filename timestamp pair to the list.
          val newFileList = fileList + (fileName -> timestamp)

          parent ! Broadcast(
            FileDuplicate(fileName = fileName, timestamp = timestamp, parent = parent, partitionSet),
            context.self,
            partitionSet
          )

          context.log.info(s"[FileUpload] File uploaded! File = $fileName , fileList = $newFileList")

          fromMap(newFileList)


        /**
         * Updates the timestap related to a file and calls broadcast such that other sites know about the update.
         */
        case FileUpdate(fileName: String, newTimestamp: String, parent: ActorRef[MasterSite.MasterSiteProtocol], partitionList: Set[ActorRef[SiteProtocol]]) =>
          // Check if the hashFile exists
          if (fileList.contains(fileName)) {

            // Increment the versionVector corresponding to originPointer by 1.
            val newFileList = updateFileList(fileList = fileList, fileName = fileName, newVal = newTimestamp)

            context.log.info(s"[FileUpdate] File $fileName is updated. fileList becomes = $newFileList")
            parent ! Broadcast(
              FileUpdatedConfirm(fileName = fileName, updatedTimestamp = newTimestamp, siteActor = context.self),
              context.self,
              partitionList
            )

            fromMap(newFileList)
          } else {
            context.log.error(s"[FileUpdate] File = $fileName does not exist in fileList = $fileList")
            fromMap(fileList)
          }


        /**
         * Duplicates file onto current site and broadcast updated filelist to other sites.
         */
        case FileDuplicate(fileName: String, timestamp: String, parent, partitionSet: Set[ActorRef[SiteProtocol]]) =>
          val siteName = context.self.path.name
          // Check if fileList actually keeps track of the file
          if (!fileList.contains(fileName)) {
            val newFileList = fileList + (fileName -> timestamp)
            context.log.info(s"[FileDuplicate] site $siteName has duplicated $fileName at timestamp $timestamp. fileList $newFileList.")

            parent ! Broadcast(
              FileDuplicate(fileName = fileName, timestamp = timestamp, parent = parent, partitionSet),
              context.self,
              partitionSet
            )

            fromMap(newFileList)
          } else {
            val newFileList = mergeFileList(fileList, fileName, timestamp)
            context.log.info(s"[FileDuplicate] site $siteName has file with name $fileName. fileList $newFileList.")
            fromMap(newFileList)
          }



        /**
         * Confirms a file updated has succeeded.
         */
        case FileUpdatedConfirm(fileName: String, updatedTimestamp: String, fromSite) =>
          if (fileList.contains(fileName)) {
            val newFileList = updateFileList(fileList, fileName, updatedTimestamp)

            context.log.info(s"[FileUpdatedConfirm] File = $fileName, version = $updatedTimestamp, newFileList = $newFileList. Site ${context.self.path.name}")
            fromMap(newFileList)
          } else {
            context.log.error(s"File = $fileName not in fileList = $fileList. newVersion $updatedTimestamp. Site ${context.self.path.name}")
            Behaviors.unhandled
          }



        /**
         * Merges filelist of current site and an other site.
         */
        case Merged(to, parent, partitionSet) =>
          context.log.info(s"[Merged] sending fileList of site ${context.self.path.name} to site ${to.path.name}. FileList sent: $fileList")
          to ! CheckInconsistency(fileList, parent, partitionSet)
          fromMap(fileList)



        /**
         * Performs inconsistency detection for the timestamp algorithm.
         */
        case CheckInconsistency(fromFileList, parent, partitionSet) =>
          val newFileList = inconsistencyDetection(context.log, fileList, fromFileList)
          parent ! Broadcast(
            ReplaceFileList(newFileList),
            context.self,
            partitionSet
          )
          fromMap(newFileList)



        /**
         * Replace current filelist with another.
         */
        case ReplaceFileList(newFileList) =>
          context.log.info(s"[ReplaceFileList.${context.self.path.name}] Replaced FileList with newFileList $newFileList")
          fromMap(newFileList)


        /**
         * Broadcast is done.
         */
        case BroadcastDone(msg: String) =>
          context.log.info(s"$msg")
          Behaviors.same

      }
  }

  /**
   * Helper method for merging filelists.
   * @param fileList Filelist to merge.
   * @param fileName The name of the file to check for.
   * @param timestamp Timestamp of the file.
   * @return merged filelist.
   */
  private def mergeFileList(
                             fileList: Map[String, String],
                             fileName: String,
                             timestamp: String
                           ): Map[String, String] =
  {
    if(!fileList.contains(fileName)) {
      val newFileList = fileList + (fileName -> timestamp)
      newFileList
    }
    else { // or if fileList already has the file
      var currentTimestamp = fileList(fileName)
      if(currentTimestamp < timestamp){
        currentTimestamp = timestamp
      }

      val newFileList = fileList + (fileName -> currentTimestamp)
      newFileList
    }
  }

  /**
   * Helper method for updating a filelist
   * @param fileList Filelist used by the current Site.
   * @param fileName File to change.
   * @param newVal New timestamp to change to.
   * @return updated filelist.
   */
  private def updateFileList(
                              fileList: Map[String, String],
                              fileName: String,
                              newVal: String
                            ): Map[String, String] = {
    fileList.updatedWith(key = fileName) {
      case Some(oldVal) =>
        if (oldVal < newVal) {
          Some(newVal)
        } else {
          Some(oldVal)
        }
      case None =>
        Some("")
    }
  }

  /**
   * Helper method for merging two file lists
   * @param log Logger for printing out information to the terminal.
   * @param fileListP1 File list of Partition 1.
   * @param fileListP2 File List of Partition 2.
   * @return Merged file List.
   */
  private def inconsistencyDetection(
                                      log: Logger,
                                      fileListP1: Map[String, String],
                                      fileListP2: Map[String, String]
                                    ): Map[String, String] = {

    val zippedLists = (fileListP1 ++ fileListP2).toList
    val tempList = zippedLists.groupBy(_._1).values.map {
      case List((filename, time1), (_, time2)) => (filename, time1, time2)
    }
    var fileList = Map[String, String]()

    for ((filename, time1, time2) <- tempList) {
      // Check whether one of the version vectors is dominant over the other.
      if (time1 > time2) { //TODO: Check if string comparison actually works for dates
        log.info(s"[Inconsistency Detected] For File $filename -> version conflict detected: $time1 - $time2")
        fileList = fileList + (filename -> time1)
      } else if (time1 < time2) {
        log.info(s"[Inconsistency Detected] For File $filename -> version conflict detected: $time1 - $time2")
        fileList = fileList + (filename -> time2)
      } else {
        log.info(s"[Consistency Detected] For File $filename -> no version conflict detected: $time1 - $time2")
        fileList = fileList + (filename -> time1)
      }
    }

    log.info(s"[LOGGER ID] $fileList. FL1 $fileListP1  FL2 $fileListP2")
    fileList
  }

}
