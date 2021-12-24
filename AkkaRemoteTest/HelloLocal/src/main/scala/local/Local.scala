package local

/*
import akka.actor._
import akka.util.Timeout
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Success

object Local extends App {

  implicit val system = ActorSystem("LocalSystem")
  val localActor = system.actorOf(Props[LocalActor], name = "LocalActor")  // the local actor
  localActor ! "START"                                                     // start the action

}

class LocalActor extends Actor {

  // create the remote actor
  val remote = context.actorSelection("akka.tcp://HelloRemoteSystem@127.0.0.1:5150/user/RemoteActor")
  var counter = 0


implicit val timeout = Timeout(1.seconds)
implicit val ec: ExecutionContext = ExecutionContext.global

//context watch remote.resolveOne().map(a => a.)
remote.resolveOne().onComplete({
  case Success(value) =>
    context.watch(value)
  case _ =>
    None
})


  def receive = {
    case "START" => 
        remote ! "Hello from the LocalActor"
    case Terminated(child) =>
      println(s"peer is dead !!!!")
    case msg: String => 
        println(s"LocalActor received message: '$msg'")
        if (counter < 1) {
            sender ! "Hello back to you"
            counter += 1
        }
  }
}
*/

import akka.Done
import akka.actor.typed.receptionist.Receptionist.Find
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorRefResolver, ActorSystem, Behavior, SupervisorStrategy, Terminated}
import akka.util.Timeout
import local.LocalActor.LocalKey
import remote.RemoteActor.RemoteKey
import remote.msg
import remote._

import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object Local {
  def main(args: Array[String]): Unit = {
    val _ = ActorSystem(
      Behaviors.setup[Done] { implicit ctx =>
        implicit val system: ActorSystem[_]             = ctx.system
        implicit val executionContext: ExecutionContext = system.executionContext

        //val actorRefResolver = ActorRefResolver(system)
        //val remoteActor = system.actorOf(Props[RemoteActor], name = "RemoteActor")
        val localActor      = ctx.spawn(LocalActor(), "LocalActor")
        localActor ! remote.Response("start")

        Behaviors.receiveMessage { case Done => Behaviors.stopped }
      },
      "LocalActor"
    )
  }
}

case class LocalActor(ctx:ActorContext[remote.msg]) extends AbstractBehavior[remote.msg](ctx)
{

  var count = 0
  val actorRefResolver = ActorRefResolver(ctx.system)
  val serializedActorRef: Array[Byte]= actorRefResolver.toSerializationFormat(ctx.self).getBytes(StandardCharsets.UTF_8)
  val str = new String(serializedActorRef, StandardCharsets.UTF_8)

  //val remoteref = actorRefResolver.resolveActorRef[remote.msg]("akka://HelloRemote@127.0.0.1:25520/user/RemoteActor")
  //context.watch(remoteref)

  ctx.system.receptionist ! Receptionist.Register(LocalKey, ctx.self)

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
      case Request(replyTo, response: Response) =>
        println(s"receive $response")
        println(str)
        //replyTo ! str
        this
      case Show(value) =>
        println(s"receive show $value")
        val remoteref = actorRefResolver.resolveActorRef[msg](value)
        context.watch(remoteref)
        this
      case Terminated(remoteRef) =>
        println("dead")
        this
      case Response(value) =>
        println(value)
        println(s"self $str")
        this
    }
}

object LocalActor {

  val LocalKey = ServiceKey[remote.msg]("LocalActor")
  def apply(): Behavior[remote.msg] =
    Behaviors
      .supervise[remote.msg] {
        Behaviors.setup[remote.msg] { ctx =>



          LocalActor(ctx)


        }
      }
      .onFailure[Exception](
        SupervisorStrategy.restartWithBackoff(minBackoff = 10.second, maxBackoff = 5.minute, randomFactor = 0.2)
      )
}

