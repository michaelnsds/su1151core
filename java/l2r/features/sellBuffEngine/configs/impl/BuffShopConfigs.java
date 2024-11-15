package l2r.features.sellBuffEngine.configs.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import gr.sr.configsEngine.AbstractConfigs;
import gr.sr.utils.L2Properties;

/**
 * @author vGodFather
 */
public class BuffShopConfigs extends AbstractConfigs
{
	private static final String BUFF_SHOP_CONFIG_FILE = "./config/extra/BuffShop.ini";
	
	public static List<Integer> BUFFSHOP_ALLOW_CLASS;
	public static List<Integer> BUFFSHOP_FORBIDDEN_SKILL;
	public static int BUFFSHOP_BUFFS_MAX_COUNT;
	
	@Override
	public void loadConfigs()
	{
		// Load BuffShop L2Properties file (if exists)
		L2Properties L2SellBuffProperties = new L2Properties();
		final File l2jsellbuff = new File(BUFF_SHOP_CONFIG_FILE);
		try (InputStream is = new FileInputStream(l2jsellbuff))
		{
			L2SellBuffProperties.load(is);
		}
		catch (Exception e)
		{
			_log.error("Error while loading BuffShop settings!", e);
		}
		
		BUFFSHOP_BUFFS_MAX_COUNT = Integer.parseInt(L2SellBuffProperties.getProperty("MaxCount", "8"));
		
		String[] classIds = L2SellBuffProperties.getProperty("BuffShopAllowClassId", "").split(",");
		BUFFSHOP_ALLOW_CLASS = new ArrayList<>(classIds.length);
		for (String id : classIds)
		{
			try
			{
				BUFFSHOP_ALLOW_CLASS.add(Integer.parseInt(id.trim()));
			}
			catch (NumberFormatException e)
			{
				_log.info("BuffShop System: Error parsing Class id. Skipping " + id + ".");
			}
		}
		
		String[] skillIds = L2SellBuffProperties.getProperty("BuffShopForbiddenSkill", "").split(",");
		BUFFSHOP_FORBIDDEN_SKILL = new ArrayList<>(skillIds.length);
		for (String id : skillIds)
		{
			try
			{
				BUFFSHOP_FORBIDDEN_SKILL.add(Integer.parseInt(id.trim()));
			}
			catch (NumberFormatException e)
			{
				_log.info("BuffShop System: Error parsing Skill id. Skipping " + id + ".");
			}
		}
	}
	
	public static BuffShopConfigs getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final BuffShopConfigs _instance = new BuffShopConfigs();
	}
}