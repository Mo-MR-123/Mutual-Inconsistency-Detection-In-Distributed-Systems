package com.akkamidd

import akka.actor.typed.ActorSystem
import com.akkamidd.actors.MasterSite
import com.akkamidd.actors.MasterSite.MasterSiteProtocol

object AkkaMain extends App {
  val masterSite: ActorSystem[MasterSiteProtocol] = ActorSystem(MasterSite(), "MasterSite")
}


