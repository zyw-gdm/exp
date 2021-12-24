package remote
/*
import akka.actor.typed._
//import akka.actor._
import java.nio.charset.StandardCharsets

import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Success

object HelloRemote extends App  {
  val system = ActorSystem(Behaviors.empty, "HelloRemoteSystem")
  //val actorRefResolver = ActorRefResolver(system)
  val remoteActor = system.actorOf(Props[RemoteActor], name = "RemoteActor")
  remoteActor ! "The RemoteActor is alive"
}

class RemoteActor extends Actor {
  var count = 0
  def receive = {
    case msg: String if count < 1 =>
        println(s"RemoteActor received message '$msg'")

        //val serializedActorRef: Array[Byte]= actorRefResolver.toSerializationFormat(ctx.self).getBytes(StandardCharsets.UTF_8)

        //val str = new String(serializedActorRef, StandardCharsets.UTF_8)
        sender ! str
        count += 1
  }

}
*/

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRefResolver, ActorSystem}

import scala.concurrent.ExecutionContext
import akka.actor.typed._
import akka.Done
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.adapter.TypedActorContextOps
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.util.Timeout
import remote.RemoteActor.RemoteKey

import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.Success





object HelloRemote {
  def main(args: Array[String]): Unit = {
    val _ = ActorSystem(
      Behaviors.setup[Done] { implicit ctx =>
        implicit val system: ActorSystem[_]             = ctx.system
        implicit val executionContext: ExecutionContext = system.executionContext

        val actorRefResolver = ActorRefResolver(system)
        //val remoteActor = system.actorOf(Props[RemoteActor], name = "RemoteActor")
        val remoteActor = ctx.spawn(RemoteActor(), "RemoteActor")
        remoteActor ! Response("show")

        Behaviors.receiveMessage { case Done => Behaviors.stopped }
      },
      "HelloRemote"
    )
  }
}
sealed trait  msg
case class Response(result: String) extends  msg
case class Request(query: String, replyTo: ActorRef[Response]) extends  msg
case class Show(value:String) extends msg
case class ListingResponse(listing: Receptionist.Listing) extends msg
case class RemoteActor(ctx:ActorContext[msg]) extends AbstractBehavior[msg](ctx)
{
  var count = 0
  val actorRefResolver = ActorRefResolver(ctx.system)
  val serializedActorRef: Array[Byte]= actorRefResolver.toSerializationFormat(ctx.self).getBytes(StandardCharsets.UTF_8)
  val str = new String(serializedActorRef, StandardCharsets.UTF_8)
  //val local = context.toClassic.actorSelection("akka.tcp://LocalActor@127.0.0.1:5151/user/LocalActor")
  val local = context.toClassic.actorSelection("akka://LocalActor@127.0.0.1:25521/user/LocalActor")
  implicit val timeout = Timeout(1.seconds)
  implicit val ec: ExecutionContext = ExecutionContext.global
  /*
  local.resolveOne().onComplete({
    case Success(value) =>
      ctx.watch[msg](value.asInstanceOf[ActorRef[msg]])
    case _ =>
      None
  })
  */

  ctx.system.receptionist ! Receptionist.Register(RemoteKey, ctx.self)
  val listingAdapter: ActorRef[Receptionist.Listing] = ctx.messageAdapter {
    listing =>
      println(s"listingAdapter:listing: ${listing.toString}")
      ListingResponse(listing)
  }
  ctx.system.receptionist ! Receptionist.Subscribe(RemoteKey, listingAdapter)
  var remote:Option[ActorRef[msg]] = None
  ctx.system.receptionist ! Receptionist.Find(RemoteKey, listingAdapter)

  override def onMessage(msg: msg): Behavior[msg] =
    msg match {
      case ListingResponse(RemoteKey.Listing(listings)) =>
        println(s"Got listing Response message with size ${listings.size}")
        val xs: Set[ActorRef[msg]] = listings
        for (x<-xs) {
          remote = Some(x)

        }
        this
      case Request(query: String, replyTo: ActorRef[Response]) =>
        println(s"receive $query")
        println(str)
        replyTo ! Response(str)
        this
      case Response(value) =>
        println(s"receive response: $value")
        if (value == "show") {
          println(s"sending self $str to local $local")
          local ! Show(str)
        }
        //ctx.self ! Request("receive start", ctx.self)
        this
    }
}

object RemoteActor {
  val RemoteKey = ServiceKey[remote.msg]("RemoteActor")
  def apply(): Behavior[msg] =
    Behaviors
      .supervise[msg] {
        Behaviors.setup[msg] { ctx =>

          RemoteActor(ctx)

        }
      }
      .onFailure[Exception](
        SupervisorStrategy.restartWithBackoff(minBackoff = 10.second, maxBackoff = 5.minute, randomFactor = 0.2)
      )


}



