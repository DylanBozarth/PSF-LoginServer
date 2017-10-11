// Copyright (c) 2017 PSForever
import akka.actor.{Actor, Props}
import net.psforever.objects.serverobject.doors.Door
import net.psforever.objects.zones.{DoorCloseActor, Zone}
import net.psforever.packet.game.PlanetSideGUID

object LocalAction {
  trait Action

  final case class DoorOpens(player_guid : PlanetSideGUID, continent : Zone, door : Door) extends Action
  final case class DoorCloses(player_guid : PlanetSideGUID, door_guid : PlanetSideGUID) extends Action
}

object LocalServiceResponse {
  trait Response

  final case class DoorOpens(door_guid : PlanetSideGUID) extends Response
  final case class DoorCloses(door_guid : PlanetSideGUID) extends Response
}

final case class LocalServiceMessage(forChannel : String, actionMessage : LocalAction.Action)

final case class LocalServiceResponse(toChannel : String, avatar_guid : PlanetSideGUID, replyMessage : LocalServiceResponse.Response) extends GenericEventBusMsg

/*
   /LocalEnvironment/
 */

class LocalService extends Actor {
  //import LocalService._
  private val doorCloser = context.actorOf(Props[DoorCloseActor], "local-door-closer")
  private [this] val log = org.log4s.getLogger

  override def preStart = {
    log.info("Starting...")
  }

  val LocalEvents = new GenericEventBus[LocalServiceResponse]

  def receive = {
    case Service.Join(channel) =>
      val path = s"/$channel/LocalEnvironment"
      val who = sender()
      log.info(s"$who has joined $path")
      LocalEvents.subscribe(who, path)
    case Service.Leave() =>
      LocalEvents.unsubscribe(sender())
    case Service.LeaveAll() =>
      LocalEvents.unsubscribe(sender())

    case LocalServiceMessage(forChannel, action) =>
      action match {
        case LocalAction.DoorOpens(player_guid, zone, door) =>
          doorCloser ! DoorCloseActor.DoorIsOpen(door, zone)
          LocalEvents.publish(
            LocalServiceResponse(s"/$forChannel/LocalEnvironment", player_guid, LocalServiceResponse.DoorOpens(door.GUID))
          )

        case LocalAction.DoorCloses(player_guid, door_guid) =>
          LocalEvents.publish(
            LocalServiceResponse(s"/$forChannel/LocalEnvironment", player_guid, LocalServiceResponse.DoorCloses(door_guid))
          )
        case _ => ;
      }

    //response from DoorCloseActor
    case DoorCloseActor.CloseTheDoor(door_guid, zone_id) =>
      LocalEvents.publish(
        LocalServiceResponse(s"/$zone_id/LocalEnvironment", PlanetSideGUID(0), LocalServiceResponse.DoorCloses(door_guid))
      )

    case msg =>
      log.info(s"Unhandled message $msg from $sender")
  }
}
