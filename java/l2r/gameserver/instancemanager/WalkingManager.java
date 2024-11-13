package l2r.gameserver.instancemanager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import l2r.Config;
import l2r.gameserver.GeoData;
import l2r.gameserver.ThreadPoolManager;
import l2r.gameserver.enums.CtrlIntention;
import l2r.gameserver.instancemanager.tasks.StartMovingTask;
import l2r.gameserver.model.Location;
import l2r.gameserver.model.actor.L2Npc;
import l2r.gameserver.model.actor.instance.L2MonsterInstance;
import l2r.gameserver.model.actor.tasks.npc.walker.ArrivedTask;
import l2r.gameserver.model.events.EventDispatcher;
import l2r.gameserver.model.events.impl.character.npc.OnNpcMoveNodeArrived;
import l2r.gameserver.model.holders.NpcRoutesHolder;
import l2r.gameserver.model.walk.RepeatType;
import l2r.gameserver.model.walk.WalkInfo;
import l2r.gameserver.model.walk.WalkNode;
import l2r.gameserver.model.walk.WalkRoute;
import l2r.gameserver.network.NpcStringId;
import l2r.gameserver.network.clientpackets.Say2;
import l2r.gameserver.network.serverpackets.NpcSay;
import l2r.gameserver.util.Broadcast;
import l2r.util.data.xml.IXmlReader.IXmlReader;

import gr.sr.logging.Log;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * This class manages walking monsters.
 * @author vGodFather
 */
public final class WalkingManager implements IXmlReader
{
	private final Map<String, WalkRoute> _routes = new HashMap<>(); // all available routes
	private final Map<Integer, WalkInfo> _activeRoutes = new HashMap<>(); // each record represents NPC, moving by predefined route from _routes, and moving progress
	private final Map<Integer, NpcRoutesHolder> _routesToAttach = new HashMap<>(); // each record represents NPC and all available routes for it
	
	protected WalkingManager()
	{
		load();
	}
	
	@Override
	public final void load()
	{
		parseDatapackFile("data/xml/other/Routes.xml");
		LOGGER.info(getClass().getSimpleName() + ": Loaded " + _routes.size() + " walking routes.");
	}
	
	@Override
	public void parseDocument(Document doc)
	{
		Node n = doc.getFirstChild();
		for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling())
		{
			if (d.getNodeName().equals("route"))
			{
				final String routeName = parseString(d.getAttributes(), "name");
				boolean repeat = parseBoolean(d.getAttributes(), "repeat");
				final String repeatStyle = d.getAttributes().getNamedItem("repeatStyle").getNodeValue().toLowerCase();
				final RepeatType repeatType = RepeatType.findByName(repeatStyle.toLowerCase());
				
				final List<WalkNode> list = new ArrayList<>();
				for (Node r = d.getFirstChild(); r != null; r = r.getNextSibling())
				{
					if (r.getNodeName().equals("point"))
					{
						NamedNodeMap attrs = r.getAttributes();
						int x = parseInteger(attrs, "X");
						int y = parseInteger(attrs, "Y");
						int z = parseInteger(attrs, "Z");
						int delay = parseInteger(attrs, "delay");
						boolean run = parseBoolean(attrs, "run");
						NpcStringId npcString = null;
						String chatString = null;
						
						Node node = attrs.getNamedItem("string");
						if (node != null)
						{
							chatString = node.getNodeValue();
						}
						else
						{
							node = attrs.getNamedItem("npcString");
							if (node != null)
							{
								npcString = NpcStringId.getNpcStringId(node.getNodeValue());
								if (npcString == null)
								{
									LOGGER.warn(getClass().getSimpleName() + ": Unknown npcString '" + node.getNodeValue() + "' for route '" + routeName + "'");
									continue;
								}
							}
							else
							{
								node = attrs.getNamedItem("npcStringId");
								if (node != null)
								{
									npcString = NpcStringId.getNpcStringId(Integer.parseInt(node.getNodeValue()));
									if (npcString == null)
									{
										LOGGER.warn(getClass().getSimpleName() + ": Unknown npcString '" + node.getNodeValue() + "' for route '" + routeName + "'");
										continue;
									}
								}
							}
						}
						list.add(new WalkNode(x, y, z, delay, run, npcString, chatString));
					}
					
					else if (r.getNodeName().equals("target"))
					{
						NamedNodeMap attrs = r.getAttributes();
						try
						{
							int npcId = Integer.parseInt(attrs.getNamedItem("id").getNodeValue());
							int x = Integer.parseInt(attrs.getNamedItem("spawnX").getNodeValue());
							int y = Integer.parseInt(attrs.getNamedItem("spawnY").getNodeValue());
							int z = Integer.parseInt(attrs.getNamedItem("spawnZ").getNodeValue());
							
							NpcRoutesHolder holder = _routesToAttach.containsKey(npcId) ? _routesToAttach.get(npcId) : new NpcRoutesHolder();
							holder.addRoute(routeName, new Location(x, y, z));
							_routesToAttach.put(npcId, holder);
						}
						catch (Exception e)
						{
							LOGGER.warn(getClass().getSimpleName() + ": Error in target definition for route '" + routeName + "'");
						}
					}
				}
				_routes.put(routeName, new WalkRoute(routeName, list, repeat, false, repeatType));
			}
		}
	}
	
	/**
	 * @param npc NPC to check
	 * @return {@code true} if given NPC, or its leader is controlled by Walking Manager and moves currently.
	 */
	public boolean isOnWalk(L2Npc npc)
	{
		L2MonsterInstance monster = null;
		
		if (npc.isMonster())
		{
			if (((L2MonsterInstance) npc).getLeader() == null)
			{
				monster = (L2MonsterInstance) npc;
			}
			else
			{
				monster = ((L2MonsterInstance) npc).getLeader();
			}
		}
		
		if (((monster != null) && !isRegistered(monster)) || !isRegistered(npc))
		{
			return false;
		}
		
		final WalkInfo walk = monster != null ? _activeRoutes.get(monster.getObjectId()) : _activeRoutes.get(npc.getObjectId());
		if (walk.isStoppedByAttack() || walk.isSuspended())
		{
			return false;
		}
		return true;
	}
	
	public WalkRoute getRoute(String route)
	{
		return _routes.get(route);
	}
	
	/**
	 * @param npc NPC to check
	 * @return {@code true} if given NPC controlled by Walking Manager.
	 */
	public boolean isRegistered(L2Npc npc)
	{
		return _activeRoutes.containsKey(npc.getObjectId());
	}
	
	/**
	 * @param npc
	 * @return name of route
	 */
	public String getRouteName(L2Npc npc)
	{
		return _activeRoutes.containsKey(npc.getObjectId()) ? _activeRoutes.get(npc.getObjectId()).getRoute().getName() : "";
	}
	
	/**
	 * Start to move given NPC by given route
	 * @param npc NPC to move
	 * @param routeName name of route to move by
	 */
	public void startMoving(final L2Npc npc, final String routeName)
	{
		if (npc == null)
		{
			return;
		}
		
		if (npc.isDead())
		{
			// LOGGER.error(getClass().getSimpleName() + ": Failed start movement for NPC[DEAD] ID: " + npc.getId() + " : " + npc.getObjectId());
			return;
		}
		
		if (npc.isDead())
		{
			// LOGGER.error(getClass().getSimpleName() + ": Failed start movement for NPC[DECAYED] ID: " + npc.getId() + " : " + npc.getObjectId());
			return;
		}
		
		if (!npc.isVisible())
		{
			// LOGGER.error(getClass().getSimpleName() + ": Failed start movement for NPC[NULL REGION] ID: " + npc.getId() + " : " + npc.getObjectId());
			return;
		}
		
		if (_routes.containsKey(routeName)) // check, if these route and NPC present
		{
			if (!_activeRoutes.containsKey(npc.getObjectId())) // new walk task
			{
				// only if not already moved / not engaged in battle... should not happens if called on spawn
				if ((npc.getAI().getIntention() == CtrlIntention.AI_INTENTION_ACTIVE) || (npc.getAI().getIntention() == CtrlIntention.AI_INTENTION_IDLE))
				{
					final WalkInfo walk = new WalkInfo(routeName);
					
					if (npc.isDebug())
					{
						walk.setLastAction(System.currentTimeMillis());
					}
					
					WalkNode node = walk.getCurrentNode();
					
					// adjust next waypoint, if NPC spawns at first waypoint
					if ((npc.getX() == node.getX()) && (npc.getY() == node.getY()))
					{
						walk.calculateNextNode(npc);
						node = walk.getCurrentNode();
						npc.sendDebugMessage("Route '" + routeName + "': spawn point is same with first waypoint, adjusted to next");
					}
					
					if (npc.isInsideRadius(node, 20, false, false))
					{
						walk.calculateNextNode(npc);
						node = walk.getCurrentNode();
						npc.sendDebugMessage("Route '" + routeName + "': first point is the same with first waypoint, adjusted to next");
					}
					
					npc.sendDebugMessage("Starting to move at route '" + routeName + "'");
					npc.setIsRunning(node.runToLocation());
					npc.getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, node);
					walk.setWalkCheckTask(ThreadPoolManager.getInstance().scheduleAiAtFixedRate(new StartMovingTask(npc, routeName), 60000, 60000)); // start walk check task, for resuming walk after fight
					
					npc.getKnownList().startTrackingTask();
					
					_activeRoutes.put(npc.getObjectId(), walk); // register route
				}
				else
				{
					npc.sendDebugMessage("Failed to start moving along route '" + routeName + "', scheduled");
					ThreadPoolManager.getInstance().scheduleGeneral(new StartMovingTask(npc, routeName), 60000);
				}
			}
			else
			// walk was stopped due to some reason (arrived to node, script action, fight or something else), resume it
			{
				if (_activeRoutes.containsKey(npc.getObjectId()) && ((npc.getAI().getIntention() == CtrlIntention.AI_INTENTION_ACTIVE) || (npc.getAI().getIntention() == CtrlIntention.AI_INTENTION_IDLE)))
				{
					final WalkInfo walk = _activeRoutes.get(npc.getObjectId());
					if (walk == null)
					{
						return;
					}
					
					// Prevent call simultaneously from scheduled task and onArrived() or temporarily stop walking for resuming in future
					if (walk.isBlocked() || walk.isSuspended())
					{
						npc.sendDebugMessage("Failed to continue moving along route '" + routeName + "' (operation is blocked)");
						return;
					}
					
					try
					{
						walk.setBlocked(true);
						final WalkNode node = walk.getCurrentNode();
						npc.sendDebugMessage("Route '" + routeName + "', continuing to node " + walk.getCurrentNodeId());
						npc.setIsRunning(node.runToLocation());
						
						if (Config.GEODATA)
						{
							if (GeoData.getInstance().canMove(npc, node) || (GeoData.getInstance().findMovePath(npc.getX(), npc.getY(), npc.getZ(), node.clone(), npc, true, npc.getInstanceId()).size() > 0))
							{
								npc.getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, node);
							}
							else
							{
								npc.teleToLocation(node);
							}
						}
						else
						{
							npc.getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, node);
						}
						
						walk.setBlocked(false);
						walk.setStoppedByAttack(false);
					}
					catch (Exception e)
					{
						// LOGGER.error(getClass().getSimpleName() + ": Failed start movement for NPC ID: " + npc.getId(), e);
						npc.getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
					}
				}
				else
				{
					npc.sendDebugMessage("Failed to continue moving along route '" + routeName + "' (wrong AI state - " + npc.getAI().getIntention() + ")");
				}
			}
		}
	}
	
	/**
	 * Cancel NPC moving permanently
	 * @param npc NPC to cancel
	 */
	public synchronized void cancelMoving(L2Npc npc)
	{
		final WalkInfo walk = _activeRoutes.remove(npc.getObjectId());
		if (walk != null)
		{
			walk.getWalkCheckTask().cancel(true);
			npc.getKnownList().stopTrackingTask();
		}
	}
	
	/**
	 * Resumes previously stopped moving
	 * @param npc NPC to resume
	 */
	public void resumeMoving(final L2Npc npc)
	{
		final WalkInfo walk = _activeRoutes.get(npc.getObjectId());
		if (walk != null)
		{
			walk.setSuspended(false);
			walk.setStoppedByAttack(false);
			startMoving(npc, walk.getRoute().getName());
		}
	}
	
	/**
	 * Pause NPC moving until it will be resumed
	 * @param npc NPC to pause moving
	 * @param suspend {@code true} if moving was temporarily suspended for some reasons of AI-controlling script
	 * @param stoppedByAttack {@code true} if moving was suspended because of NPC was attacked or desired to attack
	 */
	public void stopMoving(L2Npc npc, boolean suspend, boolean stoppedByAttack)
	{
		L2MonsterInstance monster = null;
		
		if (npc.isMonster())
		{
			if (((L2MonsterInstance) npc).getLeader() == null)
			{
				monster = (L2MonsterInstance) npc;
			}
			else
			{
				monster = ((L2MonsterInstance) npc).getLeader();
			}
		}
		
		if (((monster != null) && !isRegistered(monster)) || !isRegistered(npc))
		{
			return;
		}
		
		final WalkInfo walk = monster != null ? _activeRoutes.get(monster.getObjectId()) : _activeRoutes.get(npc.getObjectId());
		
		walk.setSuspended(suspend);
		walk.setStoppedByAttack(stoppedByAttack);
		
		if (monster != null)
		{
			monster.stopMove(null);
			monster.getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
		}
		else
		{
			npc.stopMove(null);
			npc.getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
		}
	}
	
	/**
	 * Manage "node arriving"-related tasks: schedule move to next node; send ON_NODE_ARRIVED event to Quest script
	 * @param npc NPC to manage
	 */
	public void onArrived(final L2Npc npc)
	{
		if (_activeRoutes.containsKey(npc.getObjectId()))
		{
			// Notify quest
			EventDispatcher.getInstance().notifyEventAsync(new OnNpcMoveNodeArrived(npc), npc);
			
			final WalkInfo walk = _activeRoutes.get(npc.getObjectId());
			
			// Opposite should not happen... but happens sometime
			if (walk == null)
			{
				Log.warning("Could not find active route for npc: " + npc.getId() + " : " + npc.getSpawn().getLocation());
				return;
			}
			
			if ((walk.getCurrentNodeId() >= 0) && (walk.getCurrentNodeId() < walk.getRoute().getNodesCount()))
			{
				final WalkNode node = walk.getRoute().getNodeList().get(walk.getCurrentNodeId());
				if (npc.isInsideRadius(node, 50, false, false))
				{
					npc.sendDebugMessage("Route '" + walk.getRoute().getName() + "', arrived to node " + walk.getCurrentNodeId());
					npc.sendDebugMessage("Done in " + ((System.currentTimeMillis() - walk.getLastAction()) / 1000) + " s");
					walk.calculateNextNode(npc);
					walk.setBlocked(true); // prevents to be ran from walk check task, if there is delay in this node.
					
					if (node.getNpcString() != null)
					{
						Broadcast.toKnownPlayers(npc, new NpcSay(npc, Say2.NPC_ALL, node.getNpcString()));
					}
					else if (!node.getChatText().isEmpty())
					{
						Broadcast.toKnownPlayers(npc, new NpcSay(npc, Say2.NPC_ALL, node.getChatText()));
					}
					
					if (npc.isDebug())
					{
						walk.setLastAction(System.currentTimeMillis());
					}
					ThreadPoolManager.getInstance().scheduleGeneral(new ArrivedTask(npc, walk), 100 + (node.getDelay() * 1000L));
				}
			}
		}
	}
	
	/**
	 * Manage "on death"-related tasks: permanently cancel moving of died NPC
	 * @param npc NPC to manage
	 */
	public void onDeath(L2Npc npc)
	{
		cancelMoving(npc);
	}
	
	/**
	 * Manage "on spawn"-related tasks: start NPC moving, if there is route attached to its spawn point
	 * @param npc NPC to manage
	 */
	public void onSpawn(L2Npc npc)
	{
		if (_routesToAttach.containsKey(npc.getId()))
		{
			final String routeName = _routesToAttach.get(npc.getId()).getRouteName(npc);
			if (!routeName.isEmpty())
			{
				startMoving(npc, routeName);
			}
		}
	}
	
	public static final WalkingManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final WalkingManager _instance = new WalkingManager();
	}
}