// Copyright (c) 2017 PSForever
package net.psforever.objects.serverobject.structures

import akka.actor.ActorContext
import net.psforever.objects.serverobject.PlanetSideServerObject
import net.psforever.objects.{GlobalDefinitions, NtuContainer, SpawnPoint}
import net.psforever.objects.zones.Zone
import net.psforever.packet.game.BuildingInfoUpdateMessage
import net.psforever.types.{PlanetSideEmpire, PlanetSideGeneratorState, Vector3}
import akka.actor.typed.scaladsl.adapter._
import net.psforever.actors.zone.BuildingActor
import net.psforever.objects.definition.ObjectDefinition

import scala.collection.mutable

class WarpGate(name: String, building_guid: Int, map_id: Int, zone: Zone, buildingDefinition: WarpGateDefinition)
    extends Building(name, building_guid, map_id, zone, StructureType.WarpGate, buildingDefinition)
    with NtuContainer
    with SpawnPoint {
  /** can this building be used as an active warp gate */
  private var active: Boolean = true

  /** what faction views this warp gate as a broadcast gate */
  private var broadcast: mutable.Set[PlanetSideEmpire.Value] = mutable.Set.empty[PlanetSideEmpire.Value]

  override def infoUpdateMessage(): BuildingInfoUpdateMessage = {
    BuildingInfoUpdateMessage(
      Zone.Number,
      MapId,
      0,
      false,
      PlanetSideEmpire.NEUTRAL,
      0L,
      Faction,
      0, //!! Field != 0 will cause malformed packet. See class def.
      None,
      PlanetSideGeneratorState.Normal,
      true,  //TODO?
      false, //force_dome_active
      0,     //lattice_benefit
      0,     //cavern_benefit; !! Field > 0 will cause malformed packet. See class def.
      Nil,
      0,
      false,
      8, //!! Field != 8 will cause malformed packet. See class def.
      None,
      false, //boost_spawn_pain
      false  //boost_generator_pain
    )
  }

  /**
    * If a warp gate is active, it can be used to transport faction-affiliated forces between other gates.
    * For transportation of faction-opposed forces, use broadcast logic for that faction.
    * @return `true`, if the warp gate can be used for transport;
    *        `false`, otherwise
    */
  def Active: Boolean = active

  /**
    * Control whether a warp gate is usable for transporting faction-affiliated forces between other gates.
    * @param state `true`, to activate the gate;
    *             `false`, otherwise
    * @return `true`, if the gate is active;
    *        `false`, otherwise
    */
  def Active_=(state: Boolean): Boolean = {
    active = state
    Active
  }

  /**
    * Determine whether any faction interacts with this warp gate as "broadcast."
    * The gate must be active first.
    * @return `true`, if some faction sees this warp gate as a "broadcast gate";
    *        `false`, otherwise
    */
  def Broadcast: Boolean = Active && broadcast.nonEmpty

  /**
    * Determine whether a specific faction interacts with this warp gate as "broadcast."
    * The warp gate being `NEUTRAL` should allow for any polled faction to interact.
    * The gate must be active first.
    * @return `true`, if the given faction interacts with this warp gate as a "broadcast gate";
    *        `false`, otherwise
    */
  def Broadcast(faction: PlanetSideEmpire.Value): Boolean = {
    Active && (broadcast.contains(faction) || broadcast.contains(PlanetSideEmpire.NEUTRAL))
  }

  /**
    * Toggle whether the warp gate's faction-affiliated force interacts with this warp gate as "broadcast."
    * Other "broadcast" associations are not affected.
    * The gate must be active first.
    * @param bcast `true`, if the faction-affiliated force interacts with this gate as broadcast;
    *             `false`, if not
    * @return the set of all factions who interact with this warp gate as "broadcast"
    */
  def Broadcast_=(bcast: Boolean): Set[PlanetSideEmpire.Value] = {
    if (Active) {
      if (bcast) {
        broadcast += Faction
      } else {
        broadcast -= Faction
      }
    }
    broadcast.toSet
  }

  /**
    * Which factions interact with this warp gate as "broadcast?"
    * @return the set of all factions who interact with this warp gate as "broadcast"
    */
  def BroadcastFor: Set[PlanetSideEmpire.Value] = broadcast.toSet

  /**
    * Allow a faction to interact with a given warp gate as "broadcast" if it is active.
    * @param bcast the faction
    * @return the set of all factions who interact with this warp gate as "broadcast"
    */
  def BroadcastFor_=(bcast: PlanetSideEmpire.Value): Set[PlanetSideEmpire.Value] = {
    (broadcast += bcast).toSet
  }

  /**
    * Allow some factions to interact with a given warp gate as "broadcast" if it is active.
    * @param bcast the factions
    * @return the set of all factions who interact with this warp gate as "broadcast"
    */
  def BroadcastFor_=(bcast: Set[PlanetSideEmpire.Value]): Set[PlanetSideEmpire.Value] = {
    (broadcast ++= bcast).toSet
  }

  /**
    * Disallow a faction to interact with a given warp gate as "broadcast."
    * @param bcast the faction
    * @return the set of all factions who interact with this warp gate as "broadcast"
    */
  def StopBroadcastFor_=(bcast: PlanetSideEmpire.Value): Set[PlanetSideEmpire.Value] = {
    (broadcast -= bcast).toSet
  }

  /**
    * Disallow some factions to interact with a given warp gate as "broadcast."
    * @param bcast the factions
    * @return the set of all factions who interact with this warp gate as "broadcast"
    */
  def StopBroadcastFor_=(bcast: Set[PlanetSideEmpire.Value]): Set[PlanetSideEmpire.Value] = {
    (broadcast --= bcast).toSet
  }

  def Owner: PlanetSideServerObject = this

  def NtuCapacitor: Float = Definition.MaxNtuCapacitor

  def NtuCapacitor_=(value: Float): Float = NtuCapacitor

  def MaxNtuCapacitor : Float = Int.MaxValue

  override def NtuSource: Option[NtuContainer] = Some(this)

  override def hasLatticeBenefit(wantedBenefit: ObjectDefinition): Boolean = false

  override def latticeConnectedFacilityBenefits(): Set[ObjectDefinition] = Set.empty

  override def Definition: WarpGateDefinition = buildingDefinition
}

object WarpGate {
  def apply(name: String, guid: Int, map_id: Int, zone: Zone, buildingDefinition: WarpGateDefinition): WarpGate = {
    new WarpGate(name, guid, map_id, zone, buildingDefinition)
  }

  def Structure(name: String, guid: Int, map_id: Int, zone: Zone, context: ActorContext): WarpGate = {
    val obj = new WarpGate(name, guid, map_id, zone, GlobalDefinitions.warpgate)
    obj.Actor = context.spawn(BuildingActor(zone, obj), name = s"$map_id-$guid-gate").toClassic
    obj
  }

  def Structure(
      location: Vector3
  )(name: String, guid: Int, map_id: Int, zone: Zone, context: ActorContext): WarpGate = {
    val obj = new WarpGate(name, guid, map_id, zone, GlobalDefinitions.warpgate)
    obj.Position = location
    obj.Actor = context.spawn(BuildingActor(zone, obj), name = s"$map_id-$guid-gate").toClassic
    obj
  }

  def Structure(
      location: Vector3,
      buildingDefinition: WarpGateDefinition
  )(name: String, guid: Int, map_id: Int, zone: Zone, context: ActorContext): WarpGate = {
    val obj = new WarpGate(name, guid, map_id, zone, buildingDefinition)
    obj.Position = location
    obj.Actor = context.spawn(BuildingActor(zone, obj), name = s"$map_id-$guid-gate").toClassic
    obj
  }
}
