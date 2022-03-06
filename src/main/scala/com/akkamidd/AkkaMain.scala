package com.akkamidd

import akka.actor.typed.ActorSystem
import com.akkamidd.actors.MasterSite
import com.akkamidd.actors.MasterSite.{Broadcast, MainMessage}
import com.akkamidd.actors.Site.FileUpload

object AkkaMain extends App {
  val masterSite: ActorSystem[MainMessage] = ActorSystem(MasterSite(), "MasterSite")
  masterSite ! Broadcast(FileUpload("C","123"))
}


