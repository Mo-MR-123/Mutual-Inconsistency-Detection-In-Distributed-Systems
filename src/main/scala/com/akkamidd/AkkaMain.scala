package com.akkamidd

import akka.actor.typed.ActorSystem
import com.akkamidd.actors.MasterSite
import com.akkamidd.actors.MasterSite.{Broadcast, MainMessage}

object AkkaMain extends App {
  val masterSite: ActorSystem[MainMessage] = ActorSystem(MasterSite(), "MasterSite")
  masterSite ! Broadcast()
}


