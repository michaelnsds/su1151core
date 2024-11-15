package l2r.features.sellBuffEngine;

import java.util.LinkedHashMap;
import java.util.Map;

import l2r.gameserver.model.Location;
import l2r.gameserver.model.actor.instance.L2PcInstance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author vGodFather
 */
public class ShopObject
{
	private static Logger _log = LoggerFactory.getLogger(ShopObject.class);
	
	private final Map<Integer, PrivateBuff> buffs;
	private final int ownerId;
	private int objectId;
	private String title = "No Title";
	private final Location location;
	
	public void setXYZ(final int x, final int y, final int z, final int heading)
	{
		location.setXYZ(x, y, z);
		location.setHeading(heading);
	}
	
	public int getX()
	{
		return location.getX();
	}
	
	public int getY()
	{
		return location.getY();
	}
	
	public int getZ()
	{
		return location.getZ();
	}
	
	public int getHeading()
	{
		return location.getHeading();
	}
	
	public ShopObject(final int ownerId)
	{
		buffs = new LinkedHashMap<>();
		location = new Location(0, 0, 0);
		this.ownerId = ownerId;
	}
	
	public ShopObject(final L2PcInstance owner)
	{
		buffs = new LinkedHashMap<>();
		location = new Location(0, 0, 0);
		ownerId = owner.getObjectId();
	}
	
	public void addBuff(final String line)
	{
		String[] tmp = null;
		try
		{
			tmp = line.split(",");
			buffs.put(Integer.parseInt(tmp[0]), new PrivateBuff(Integer.parseInt(tmp[2]), Integer.parseInt(tmp[0]), Integer.parseInt(tmp[1])));
		}
		catch (Exception e)
		{
			_log.warn(ShopObject.class.getSimpleName() + ": Invalid buff found in buffshop table, Skipped.");
		}
	}
	
	public void addBuff(final int buffId, final int lvl, final int price)
	{
		buffs.put(buffId, new PrivateBuff(price, buffId, lvl));
	}
	
	public void removeBuff(final Integer buffId)
	{
		buffs.remove(buffId);
	}
	
	public int getPrice(final int buffId)
	{
		return buffs.get(buffId).price;
	}
	
	public void setPrice(final int buffId, final int price)
	{
		buffs.get(buffId).price = price;
	}
	
	public int getOwnerId()
	{
		return ownerId;
	}
	
	public Map<Integer, PrivateBuff> getBuffList()
	{
		return buffs;
	}
	
	public String getBuffLine()
	{
		final StringBuilder sb = new StringBuilder();
		for (final PrivateBuff buff : buffs.values())
		{
			if (sb.length() > 0)
			{
				sb.append(";");
			}
			sb.append(buff.skillid).append(",").append(buff.skillLevel).append(",").append(buff.price);
		}
		return sb.toString();
	}
	
	public void setTitle(final String title)
	{
		this.title = title;
	}
	
	public String getTitle()
	{
		return title;
	}
	
	public int getSellerObjectId()
	{
		return objectId;
	}
	
	public void setSellerObjectId(final int objectId)
	{
		this.objectId = objectId;
	}
	
	static class PrivateBuff
	{
		int price;
		int skillid;
		int skillLevel;
		
		public PrivateBuff(final int price, final int skillid, final int skillLevel)
		{
			this.price = price;
			this.skillid = skillid;
			this.skillLevel = skillLevel;
		}
	}
}
