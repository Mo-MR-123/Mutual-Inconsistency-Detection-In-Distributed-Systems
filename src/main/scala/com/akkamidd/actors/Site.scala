package com.akkamidd.actors

import akka.actor.{Actor, ActorLogging}

class Site(sideID: String) extends Actor with ActorLogging  {
  sealed trait FileMessages
  final case class FileUpload() extends FileMessages
  final case class FileDeletion() extends FileMessages
  final case class FileUpdate(hashFile: Int, siteID: String, version: Int) extends FileMessages

  override def receive: Receive = {
    case FileUpload() =>
      log.info("FileUpload")
    case FileDeletion =>
      log.info("FileDeletion")
    case FileUpdate(hashFile, siteID, version) =>
      log.info(s"$hashFile $siteID $version")
  }

}
