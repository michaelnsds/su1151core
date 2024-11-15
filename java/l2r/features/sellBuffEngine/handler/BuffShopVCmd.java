package l2r.features.sellBuffEngine.handler;

import l2r.features.sellBuffEngine.BuffShopManager;
import l2r.gameserver.handler.IVoicedCommandHandler;
import l2r.gameserver.model.actor.instance.L2PcInstance;

/**
 * @author vGodFather
 */
public class BuffShopVCmd implements IVoicedCommandHandler
{
	private static String[] _voicedCommands =
	{
		"buffshop"
	};
	
	@Override
	public boolean useVoicedCommand(final String command, final L2PcInstance activeChar, final String target)
	{
		if (command.equalsIgnoreCase("buffshop"))
		{
			BuffShopManager.getInstance().setShop(activeChar);
		}
		return true;
	}
	
	@Override
	public String[] getVoicedCommandList()
	{
		return _voicedCommands;
	}
}
