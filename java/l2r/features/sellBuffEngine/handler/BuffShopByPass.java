package l2r.features.sellBuffEngine.handler;

import l2r.features.sellBuffEngine.BuffShopManager;
import l2r.gameserver.data.xml.impl.SkillData;
import l2r.gameserver.enums.ZoneIdType;
import l2r.gameserver.handler.IBypassHandler;
import l2r.gameserver.model.L2World;
import l2r.gameserver.model.actor.L2Character;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.model.skills.L2Skill;
import l2r.gameserver.util.Util;

/**
 * @author vGodFather
 */
public class BuffShopByPass implements IBypassHandler
{
	private static final String[] COMMAND =
	{
		"buffshop"
	};
	
	@Override
	public boolean useBypass(final String command, final L2PcInstance activeChar, final L2Character bypassOrigin)
	{
		final String[] commands = command.split(" ");
		final String[] subcommands = command.split(" ", 3);
		if (commands[1].equals("add"))
		{
			final L2Skill skill = SkillData.getInstance().getSkill(Integer.parseInt(commands[2]), Integer.parseInt(commands[3]));
			if (!activeChar.getSkills().containsValue(skill))
			{
				activeChar.sendMessageS("You cannot add not learned skill!", 3);
				return false;
			}
			BuffShopManager.getInstance().addBuff(activeChar, Integer.parseInt(commands[2]), Integer.parseInt(commands[3]), Integer.parseInt(commands[4]), Integer.parseInt(commands[5]));
		}
		else if (commands[1].startsWith("setPrice"))
		{
			int page = 1;
			try
			{
				if (!Util.isDigit(commands[2]))
				{
					activeChar.sendMessage("Incorrect price, must be number");
					return false;
				}
				
				activeChar.setVar("tempBuffShopPrice", commands[2]);
				page = Integer.parseInt(commands[3]);
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Incorrect price.");
			}
			BuffShopManager.getInstance().list(activeChar, page);
		}
		else if (commands[1].equals("del"))
		{
			BuffShopManager.getInstance().removeBuff(activeChar, Integer.parseInt(commands[2]), Integer.parseInt(commands[3]));
		}
		else if (commands[1].equals("title"))
		{
			if (commands.length < 3)
			{
				activeChar.sendMessageS("You must input a title", 3);
				return false;
			}
			
			if (commands[2].length() > 20)
			{
				activeChar.sendMessageS("Too long title!", 3);
				return false;
			}
			
			BuffShopManager.getInstance().getShops().get(activeChar.getObjectId()).setTitle(subcommands[2]);
			BuffShopManager.getInstance().list(activeChar, 1);
		}
		else if (commands[1].equals("list"))
		{
			BuffShopManager.getInstance().list(activeChar, Integer.parseInt(commands[2]));
		}
		else if (commands[1].equals("start"))
		{
			if (!activeChar.isInsideZone(ZoneIdType.PEACE))
			{
				activeChar.sendMessageS("Buff Shop can be started only in PEACE zones!", 5);
				return false;
			}
			
			for (final L2PcInstance pl : activeChar.getKnownList().getKnownPlayers().values())
			{
				if (BuffShopManager.getInstance().getSellers().containsKey(pl.getObjectId()) && (pl.calculateDistance(activeChar, false, false) < 75.0))
				{
					activeChar.sendMessageS("You cannot use it around other buff shops", 5);
					return false;
				}
			}
			BuffShopManager.getInstance().startShop(activeChar);
		}
		else if (commands[1].equals("stop"))
		{
			BuffShopManager.getInstance().stopShop(activeChar);
		}
		else if (commands[1].equals("cast"))
		{
			final int sellerId = Integer.parseInt(commands[2]);
			final int buff = Integer.parseInt(commands[3]);
			final L2PcInstance seller = L2World.getInstance().getPlayer(sellerId);
			if (seller != null)
			{
				if (!Util.checkIfInRange(150, seller, activeChar, true))
				{
					activeChar.sendMessageS("Too far away from buff shop!!", 5);
					return false;
				}
				
				if (BuffShopManager.getInstance().getSellers().get(seller.getObjectId()) == activeChar.getObjectId())
				{
					activeChar.sendMessageS("You cannot use it on yourself!!", 5);
					return false;
				}
				BuffShopManager.getInstance().sellBuff(seller, activeChar, buff);
			}
		}
		return false;
	}
	
	@Override
	public String[] getBypassList()
	{
		return COMMAND;
	}
}
