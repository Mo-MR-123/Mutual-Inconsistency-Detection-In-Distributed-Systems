package com.akkamidd

import akka.actor.AbstractActor
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, TypedActorContext}
import akka.actor.typed.scaladsl.Behaviors
import com.akkamidd.GreeterMain.SayHello

import scala.collection.mutable.Map

object AkkaMain extends App {
  val greeterMain: ActorSystem[GreeterMain.SayHello] = ActorSystem(GreeterMain(), "AkkaMain")

  greeterMain ! SayHello("Charles")
}

object Site{
  sealed trait Command
  final case class Greet(whom: String, replyTo: ActorRef[Greeted]) extends Command
  final case class Greeted(whom: String, from: ActorRef[Greet]) extends Command
  final case class FileUpload(siteName:String) extends Command
  final case class FileUpdate() extends Command
  final case class FileUpdatedConfirm(siteName:String) extends Command
  var versionVector = Map[String, Int]() // eg versionVector: A->1, B->2
  //val actorName =



  //final case class

//  def apply(): Behavior[Greet] = Behaviors.receive { (context, message) =>
//    context.log.info("Hello {}!", message.whom)
//    message.replyTo ! Greeted(message.whom, context.self)
//    Behaviors.same
//  }

  def apply(): Behavior[Command] = Behaviors.receive {
    case (context, Greet(whom,replyTo)) =>
    context.log.info("Hello {}!", whom)
    replyTo ! Greeted(whom, context.self)
    Behaviors.same

    case (context, FileUpload(siteName)) =>
      context.log.info("", siteName)
      versionVector.put(siteName,0)
      context.log.info("File {} is uploaded! Element of version vector = {}",siteName,versionVector)
      Behaviors.same

    case (context, FileUpdate()) =>

      versionVector.put(context.self.path.name,versionVector(context.self.path.name)+1)
      context.log.info("File {} is requested to be updated. vv becomes ={} ", context.self.path.name,versionVector)
     // context.log.info(s"Element of version vector = $versionVector")
      Behaviors.same


  }



}

object GreeterBot {

  def apply(max: Int): Behavior[Site.Greeted] = {
    bot(0, max)
  }

  private def bot(greetingCounter: Int, max: Int): Behavior[Site.Greeted] =
    Behaviors.receive { (context, message) =>
      val n = greetingCounter + 1
      context.log.info("Greeting {} for {}", n, message.whom)
      if (n == max) {
        Behaviors.stopped
      } else {
        message.from ! Site.Greet(message.whom, context.self)
        bot(n, max)
      }
    }
}

// the master actor who spawn the sites
object GreeterMain {
  sealed trait MainMessage
  final case class SayHello(name: String) extends MainMessage
  final case class Broadcast() extends MainMessage
  def apply(): Behavior[MainMessage] =
    Behaviors.setup { context =>
      //#create-actors
      val siteA = context.spawn(Site(), "A")
      val siteB = context.spawn(Site(), "B")


      val childrenList= context.children
      for (i <- childrenList) {
        context.log.info(i.toString())
      }





      siteA ! Site.FileUpload("A")
      siteA ! Site.FileUpload("B")
      siteB ! Site.FileUpload("A")
      siteB ! Site.FileUpload("B")
      //#create-actors
      siteA ! Site.FileUpdate()
      siteA ! Site.FileUpdate()
      siteB ! Site.FileUpdate()

      Behaviors.receiveMessage {
        case  GreeterMain.Broadcast() =>
          context.log.info("hello")
          val childrenList= context.children
          for (i <- childrenList) {
            context.log.info(i.toString())
            i.asInstanceOf[ActorRef[Site]] ! Site.FileUpdate()
          }
          Behaviors.same
        case message =>
          context.log.info("hello")
          Behaviors.same


//        //#create-actors
//        val replyTo = context.spawn(GreeterBot(max = 3), message.name)
//        //#create-actors
//        siteA ! Site.Greet(message.name, replyTo)
//        siteB ! Site.Greet(message.name, replyTo)

      }
    }
}


