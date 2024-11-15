package l2r.features.sellBuffEngine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import l2r.L2DatabaseFactory;
import l2r.features.sellBuffEngine.ShopObject.PrivateBuff;
import l2r.features.sellBuffEngine.configs.impl.BuffShopConfigs;
import l2r.gameserver.cache.HtmCache;
import l2r.gameserver.data.xml.impl.ExperienceData;
import l2r.gameserver.data.xml.impl.PlayerTemplateData;
import l2r.gameserver.data.xml.impl.SkillData;
import l2r.gameserver.data.xml.impl.TransformData;
import l2r.gameserver.enums.PrivateStoreType;
import l2r.gameserver.enums.QuestSound;
import l2r.gameserver.idfactory.IdFactory;
import l2r.gameserver.model.L2World;
import l2r.gameserver.model.actor.appearance.PcAppearance;
import l2r.gameserver.model.actor.instance.L2PcInstance;
import l2r.gameserver.model.actor.templates.L2PcTemplate;
import l2r.gameserver.model.effects.L2EffectType;
import l2r.gameserver.model.items.instance.L2ItemInstance;
import l2r.gameserver.model.skills.L2Skill;
import l2r.gameserver.model.skills.L2SkillType;
import l2r.gameserver.model.skills.targets.L2TargetType;
import l2r.gameserver.network.serverpackets.MagicSkillUse;
import l2r.gameserver.network.serverpackets.NpcHtmlMessage;
import l2r.gameserver.network.serverpackets.PrivateStoreMsgSell;
import l2r.gameserver.util.HtmlUtil;
import l2r.gameserver.util.Util;
import l2r.util.Rnd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author vGodFather
 */
public class BuffShopManager
{
	private static final Logger _log = LoggerFactory.getLogger(BuffShopManager.class);
	private final Map<Integer, ShopObject> shops = new ConcurrentHashMap<>();
	private final Map<Integer, Integer> sellers = new ConcurrentHashMap<>();
	public static final Set<L2TargetType> targetCheck;
	private static final List<Integer> ALLOW_CLASS = BuffShopConfigs.BUFFSHOP_ALLOW_CLASS;
	private static final List<Integer> FORBIDDEN_SKILL = BuffShopConfigs.BUFFSHOP_FORBIDDEN_SKILL;
	private static final String SAVE_SHOP = "REPLACE INTO buffshop (`ownerId`,`buffs`,`title`,`x`,`y`,`z`,`heading`) VALUES (?,?,?,?,?,?,?)";
	private static final String LOAD_SHOPS = "SELECT ownerId,buffs,title,x,y,z,heading FROM buffshop";
	private static final String REMOVE_SHOP = "DELETE FROM buffshop WHERE ownerId=?";
	
	public void setShop(final L2PcInstance player)
	{
		if (!ALLOW_CLASS.contains(player.getClassId().getId()))
		{
			player.sendMessageS("Your class is not allowed to start buff shop!!", 4);
			return;
		}
		if (!shops.containsKey(player.getObjectId()))
		{
			shops.put(player.getObjectId(), new ShopObject(player));
		}
		player.sendPacket(new NpcHtmlMessage(HtmCache.getInstance().getHtm(player.getHtmlPrefix(), "data/html/sunrise/BuffShop/shopBuffMain.htm")));
	}
	
	public void list(final L2PcInstance activeChar, final int page)
	{
		String html = HtmCache.getInstance().getHtm(activeChar.getHtmlPrefix(), "data/html/sunrise/BuffShop/shopBuffList.htm");
		html = html.replace("<%price%>", activeChar.getVar("tempBuffShopPrice", "0"));
		html = html.replace("<%page%>", String.valueOf(page));
		final StringBuilder builder = new StringBuilder();
		final List<L2Skill> skills = new ArrayList<>();
		
		ShopObject shopObject = shops.containsKey(activeChar.getObjectId()) ? shops.get(activeChar.getObjectId()) : new ShopObject(activeChar);
		activeChar.getSkills().values().stream().filter(skill -> checkBuffCondition(skill, shopObject)).forEach(skill -> skills.add(skill));
		
		final int size = skills.size();
		int startIndex = (page - 1) * 5;
		startIndex = ((startIndex > (size - 1)) ? (size - 1) : startIndex);
		int endIndex = startIndex + 5;
		endIndex = ((endIndex > (size - 1)) ? (size - 1) : endIndex);
		
		if (!skills.isEmpty())
		{
			for (int index = startIndex; index <= endIndex; ++index)
			{
				final L2Skill skill2 = skills.get(index);
				final String image = "Icon.skill" + getIconSkill(skill2.getId());
				final boolean added = shopObject.getBuffList().containsKey(skill2.getId());
				final PrivateBuff buffInShop = added ? shopObject.getBuffList().get(skill2.getId()) : null;
				
				String tempPrice = activeChar.getVar("tempBuffShopPrice", "0");
				if (!Util.isDigit(tempPrice))
				{
					// wrong price prevent exploit
					tempPrice = "1";
				}
				
				builder.append("<tr>").append("<td width=5></td><td>").append("<img src=").append(image).append(" width=32 height=32/>").append("</td>");
				builder.append("<td fixwidth=180 align=center>").append("<font name=\"CreditTextNormal\">" + skill2.getName() + "</font>").append(" " + getPrice(buffInShop)).append("</td>");
				
				if (added)
				{
					builder.append("<td align=\"right\" valign=\"center\">").append("<button width=50 height=20 value=\"Del\" action=\"bypass -h buffshop del " + skill2.getId() + " " + page + "\" back=\"\" fore=\"L2UI_CT1.ListCTRL_DF_Title\">").append("</td>");
				}
				else
				{
					builder.append("<td align=\"right\" valign=\"center\">").append("<button width=50 height=20 value=\"Add\" action=\"bypass -h buffshop add " + skill2.getId() + " " + skill2.getLevel() + " " + tempPrice + " " + page + "\" back=\"\" fore=\"L2UI_CT1.ListCTRL_DF_Title\">").append("</td>");
				}
				builder.append("</tr>");
			}
		}
		else
		{
			builder.append("<tr>").append("<td> NO MORE BUFFS </td>").append("</tr>");
		}
		
		html = html.replace("%list%", builder.toString());
		html = html.replace("%title%", shopObject.getTitle());
		html = html.replace("%inshop%", getBuffCount(shopObject));
		html = html.replace("%prev%", startIndex > 0 ? "<button width=80 height=20 value=\"PrevPage\" action=\"bypass -h buffshop list " + (page - 1) + "\" back=\"\" fore=\"L2UI_CT1.ListCTRL_DF_Title\">" : "<button width=80 height=20 value=\"PrevPage\" action=\"\" back=\"\" fore=\"L2UI_CT1.ListCTRL_DF_Title\">");
		html = html.replace("%next%", endIndex < (size - 1) ? "<button width=80 height=20 value=\"NextPage\" action=\"bypass -h buffshop list " + (page + 1) + "\" back=\"\" fore=\"L2UI_CT1.ListCTRL_DF_Title\">" : "<button width=80 height=20 value=\"NextPage\" action=\"\" back=\"\" fore=\"L2UI_CT1.ListCTRL_DF_Title\">");
		html = html.replace("%curPage%", String.valueOf(page));
		html = html.replace("%buffsNumber%", String.valueOf(skills.size()));
		activeChar.sendPacket(new NpcHtmlMessage(html));
	}
	
	private boolean checkBuffCondition(final L2Skill skill, final ShopObject shopObject)
	{
		return ((skill.hasEffectType(L2EffectType.BUFF) || (skill.getSkillType() == L2SkillType.BUFF)) && !(skill.isBad() || skill.isOffensive() || skill.isDebuff())) && !skill.isPassive() && !BuffShopManager.targetCheck.contains(skill.getTargetType()) && !skill.isToggle() && !FORBIDDEN_SKILL.contains(skill.getId());
	}
	
	public void showShop(final L2PcInstance seller, final L2PcInstance player)
	{
		final ShopObject shop = shops.get(sellers.get(seller.getObjectId()));
		final StringBuilder sb = new StringBuilder();
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		sb.append("<html><title>Magic Shop</title><body><center>");
		sb.append(HtmlUtil.getMpGauge(270, (long) seller.getCurrentMp(), seller.getMaxMp(), true));
		sb.append("<table width=270>");
		for (final Map.Entry<Integer, PrivateBuff> buff : shop.getBuffList().entrySet())
		{
			final L2Skill skill = SkillData.getInstance().getSkill(buff.getValue().skillid, buff.getValue().skillLevel);
			if (skill == null)
			{
				continue;
			}
			String image = "Icon.skill" + getIconSkill(skill.getId());
			sb.append("<tr><td><img src=\"" + image + "\" width=32 height=32></td><td fixwidth=170><table><tr><td width=168>" + skill.getName() + " - Lv " + parseSkillLevel(skill.getLevel()) + "</td></tr><tr><td><font color=FF0000>Price: </font>" + buff.getValue().price + "</td></tr></table></td><td valign=middle><button action=\"bypass -h buffshop cast " + shop.getSellerObjectId() + " " + skill.getId() + "\" value=\"Buff me\" width=60 height=20 back=\"\" fore=\"L2UI_CT1.ListCTRL_DF_Title\"></td></tr>");
		}
		sb.append("</table>");
		sb.append("</center></body></html>");
		html.setHtml(sb.toString());
		player.sendPacket(html);
	}
	
	private String getPrice(PrivateBuff buffInShop)
	{
		return buffInShop != null ? "<font color=FE2E2E name=\"CreditTextNormal\">(" + buffInShop.price + ")</font>" : "<font color=FFFF00 name=\"CreditTextNormal\">(Not Added)</font>";
	}
	
	private String getBuffCount(ShopObject shopObject)
	{
		return String.valueOf(shopObject.getBuffList().size()) + "/" + String.valueOf(BuffShopConfigs.BUFFSHOP_BUFFS_MAX_COUNT);
	}
	
	private String parseSkillLevel(final int level)
	{
		return level > 100 ? "<font color=FFFF00>+" + String.valueOf(level % 100) + "</font>" : "<font color=FFFF00>" + String.valueOf(level % 100) + "</font>";
	}
	
	public void addBuff(final L2PcInstance player, final int skillId, final int skillLevel, final int price, int page)
	{
		ShopObject shopObject = shops.get(player.getObjectId());
		if (shopObject == null)
		{
			shopObject = new ShopObject(player);
			shops.put(player.getObjectId(), shopObject);
		}
		
		if (shopObject.getBuffList().size() >= BuffShopConfigs.BUFFSHOP_BUFFS_MAX_COUNT)
		{
			player.sendMessageS("You cannot add buffs any more!!", 5);
			return;
		}
		
		shopObject.addBuff(skillId, skillLevel, price);
		list(player, page);
	}
	
	public void removeBuff(final L2PcInstance player, final int skillId, int page)
	{
		shops.get(player.getObjectId()).removeBuff(skillId);
		list(player, page);
	}
	
	public void startShop(final L2PcInstance player)
	{
		final ShopObject shopObject = shops.get(player.getObjectId());
		if (shopObject.getBuffList().isEmpty())
		{
			player.sendMessageS("You must add at least one buff to sell!!", 5);
			return;
		}
		
		stopShop(player);
		final L2PcInstance seller = createIllusion(PlayerTemplateData.getInstance().getTemplate(40), "Buff Shop", "", new PcAppearance((byte) 1, (byte) 1, (byte) 1, true), Collections.emptyList());
		seller.getSellList().setTitle(shopObject.getTitle());
		seller.addExpAndSp(ExperienceData.getInstance().getExpForLevel(85) - seller.getExp(), 0);
		seller.setPrivateStoreType(PrivateStoreType.PACKAGE_SELL);
		seller.broadcastPacket(new PrivateStoreMsgSell(seller));
		L2World.getInstance().addPlayerToWorld(seller);
		seller.spawnMe(player.getX(), player.getY(), player.getZ());
		seller.setHeading(player.getHeading());
		seller.setCurrentHpMp(seller.getMaxHp(), seller.getMaxMp());
		seller.sitDown();
		shopObject.setXYZ(player.getX(), player.getY(), player.getZ(), player.getHeading());
		shopObject.setSellerObjectId(seller.getObjectId());
		sellers.put(seller.getObjectId(), player.getObjectId());
		TransformData.getInstance().transformPlayer(Rnd.get(118, 120), seller);
		seller.broadcastUserInfo();
		
		L2DatabaseFactory.simpleExcuter(SAVE_SHOP, shopObject.getOwnerId(), shopObject.getBuffLine(), shopObject.getTitle(), shopObject.getX(), shopObject.getY(), shopObject.getZ(), shopObject.getHeading());
	}
	
	public void stopShop(final L2PcInstance player)
	{
		final ShopObject shopObject = shops.get(player.getObjectId());
		if (shopObject != null)
		{
			L2PcInstance seller = L2World.getInstance().getPlayer(shopObject.getSellerObjectId());
			if (seller != null)
			{
				sellers.remove(seller.getObjectId());
				seller.decayMe();
				seller = null;
			}
			
			L2DatabaseFactory.simpleExcuter(REMOVE_SHOP, player.getObjectId());
			shopObject.setSellerObjectId(0);
		}
	}
	
	public void sellBuff(final L2PcInstance seller, final L2PcInstance player, final int buff)
	{
		final ShopObject shop = shops.get(sellers.get(seller.getObjectId()));
		if ((shop == null) || !shop.getBuffList().containsKey(buff))
		{
			return;
		}
		
		final ShopObject.PrivateBuff privatebuff = shop.getBuffList().get(buff);
		final L2Skill buffSkill = SkillData.getInstance().getSkill(privatebuff.skillid, privatebuff.skillLevel);
		if (buffSkill == null)
		{
			return;
		}
		final int mpconsume = buffSkill.getMpConsume();
		final int price = privatebuff.price;
		if (seller.getCurrentMp() < mpconsume)
		{
			player.sendMessageS("The seller is out of mana", 5);
			showShop(seller, player);
			return;
		}
		if (player.getAdena() < price)
		{
			player.sendMessageS("You don't have enough adena", 5);
			showShop(seller, player);
			return;
		}
		player.sendPacket(new MagicSkillUse(seller, player, privatebuff.skillid, privatebuff.skillLevel, 1500, 1500));
		seller.setCurrentMp(seller.getCurrentMp() - mpconsume);
		player.reduceAdena("BuffShop", price, seller, true);
		buffSkill.getEffects(seller, player);
		rewardAdena(shop, price);
		showShop(seller, player);
	}
	
	private void rewardAdena(final ShopObject seller, final int price)
	{
		final int ownerId = seller.getOwnerId();
		final L2PcInstance owner = L2World.getInstance().getPlayer(ownerId);
		if (owner != null)
		{
			owner.addAdena("BuffShop", price, null, true);
			owner.sendMessage("Congratulations !!Your buff selled!!");
			owner.sendPacket(QuestSound.ITEMSOUND_QUEST_MIDDLE.getPacket());
		}
		else
		{
			L2DatabaseFactory.simpleExcuter("UPDATE items SET count = count+ ? WHERE item_id=57 AND owner_id = ?", price, ownerId);
		}
	}
	
	public void restoreOfflineTraders()
	{
		try (final Connection con = L2DatabaseFactory.getInstance().getConnection();
			final PreparedStatement stmt = con.prepareStatement(LOAD_SHOPS);
			final ResultSet rset = stmt.executeQuery())
		{
			while (rset.next())
			{
				final int ownerId2 = rset.getInt("ownerId");
				final String[] tmp = rset.getString("buffs").split(";");
				final String title = rset.getString("title");
				final ShopObject object = new ShopObject(ownerId2);
				object.setTitle(title);
				for (final String line : tmp)
				{
					object.addBuff(line);
				}
				object.setXYZ(rset.getInt("x"), rset.getInt("y"), rset.getInt("z"), rset.getInt("heading"));
				shops.put(ownerId2, object);
			}
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		
		BuffShopManager._log.info(this.getClass().getSimpleName() + ": Loaded " + shops.size() + " buffshops.");
		if (!shops.isEmpty())
		{
			shops.forEach((ownerId, shop) ->
			{
				L2PcInstance seller = createIllusion(PlayerTemplateData.getInstance().getTemplate(40), "Buff Shop", "", new PcAppearance((byte) 1, (byte) 1, (byte) 1, true), Collections.emptyList());
				seller.getSellList().setTitle(shop.getTitle());
				seller.addExpAndSp(ExperienceData.getInstance().getExpForLevel(85) - seller.getExp(), 0);
				seller.setPrivateStoreType(PrivateStoreType.PACKAGE_SELL);
				seller.broadcastPacket(new PrivateStoreMsgSell(seller));
				L2World.getInstance().addPlayerToWorld(seller);
				seller.spawnMe(shop.getX(), shop.getY(), shop.getZ());
				seller.setHeading(shop.getHeading());
				seller.sitDown();
				seller.setCurrentHpMp(seller.getMaxHp(), seller.getMaxMp());
				shop.setSellerObjectId(seller.getObjectId());
				sellers.put(seller.getObjectId(), ownerId);
				TransformData.getInstance().transformPlayer(Rnd.get(118, 120), seller);
			});
		}
	}
	
	public Map<Integer, ShopObject> getShops()
	{
		return shops;
	}
	
	public Map<Integer, Integer> getSellers()
	{
		return sellers;
	}
	
	public static String getIconSkill(int skill)
	{
		String formato = "";
		if (skill == 4)
		{
			formato = "0004";
		}
		else if ((skill > 9) && (skill < 100))
		{
			formato = "00" + String.valueOf(skill);
		}
		else if ((skill > 99) && (skill < 1000))
		{
			formato = "0" + String.valueOf(skill);
		}
		else if (skill == 1517)
		{
			formato = "1536";
		}
		else if (skill == 1518)
		{
			formato = "1537";
		}
		else if (skill == 1547)
		{
			formato = "0065";
		}
		else if (skill == 2076)
		{
			formato = "0195";
		}
		else if ((skill > 4550) && (skill < 4555))
		{
			formato = "5739";
		}
		else if ((skill > 4698) && (skill < 4701))
		{
			formato = "1331";
		}
		else if ((skill > 4701) && (skill < 4704))
		{
			formato = "1332";
		}
		else if (skill == 6049)
		{
			formato = "0094";
		}
		else
		{
			formato = String.valueOf(skill);
		}
		return formato;
	}
	
	public static L2PcInstance createIllusion(final L2PcTemplate template, final String showName, final String showTitle, final PcAppearance app, final List<Integer> equipMent)
	{
		final int OID = IdFactory.getInstance().getNextId();
		final L2PcInstance player = new L2PcInstance(OID, template, String.valueOf(OID), app);
		player.setDummy(true);
		player.getAppearance().setVisibleName(showName);
		player.setName(String.valueOf(OID));
		player.setBaseClass(template.getClassId());
		player.setTitle(showTitle);
		for (final int id : equipMent)
		{
			final L2ItemInstance equip = player.addItem("copyToDummy", id, 1L, null, false);
			player.getInventory().equipItem(equip);
		}
		return player;
	}
	
	static
	{
		(targetCheck = new HashSet<>()).add(L2TargetType.SELF);
		targetCheck.add(L2TargetType.SERVITOR);
		targetCheck.add(L2TargetType.SUMMON);
		targetCheck.add(L2TargetType.CORPSE_MOB);
		targetCheck.add(L2TargetType.AURA);
		targetCheck.add(L2TargetType.AREA);
		targetCheck.add(L2TargetType.AURA_CORPSE_MOB);
		targetCheck.add(L2TargetType.AREA_CORPSE_MOB);
		targetCheck.add(L2TargetType.HOLY);
	}
	
	public static BuffShopManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final BuffShopManager _instance = new BuffShopManager();
	}
}
