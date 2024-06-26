package mc233.fun.kbbstoper;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import mc233.fun.kbbstoper.sql.SQLer;

public class Crawler {
	private static SQLer sql;
	public List<String> ID = new ArrayList<String>();
	public List<String> Time = new ArrayList<String>();
	public boolean visible = true;

	public Crawler() {
		resolveWebData();
		kickExpiredData();
	}

	public void resolveWebData() {
		String url = "https://www.klpbbs.com/forum.php?mod=misc&action=viewthreadmod&tid=" + Option.BBS_URL.getString()
				+ "&mobile=no";
		Document doc = null;
		try {
			if (Option.PROXY_ENABLE.getBoolean() == true) {
				doc = Jsoup.connect(url).proxy(Option.PROXY_IP.getString(), Option.PROXY_PORT.getInt()).get();
			} else {
				doc = Jsoup.connect(url).get();
			}
		} catch (IOException e) {
			if (Option.DEBUG.getBoolean()) {
				e.printStackTrace(); // 这里经常会因为网络连接不顺畅而报错
			}
			KBBSToper.getInstance().getLogger().warning(Message.FAILEDGETWEB.getString());
			return;// 没抓到网页就不要继续了，会空指针
		}
		Elements listclass = doc.getElementsByClass("list");// 获取一个class名为list的元素的合集
		Element list = null;
		try {
			list = listclass.get(0);// bbs顶贴列表页面只会有一个list，直接使用即可
		} catch (IndexOutOfBoundsException e) {
			this.visible = false;
			String warn = Message.FAILEDRESOLVEWEB.getString();
			if (!warn.isEmpty()) {
				KBBSToper.getInstance().getLogger().warning(Message.FAILEDRESOLVEWEB.getString());
			}
			return;
		}
		Element listbody = list.getElementsByTag("tbody").get(0);// tbody表示表的身体而不是表头
		for (Element rows : listbody.getElementsByTag("tr")) {// tr是表的一行
			Elements cells = rows.getElementsByTag("td");// td表示一行的单元格，cells为单元格的合集
			String action = cells.get(2).text();
			if (!(action.equals("提升(提升卡)")||action.equals("提升(服务器/交易代理提升卡)"))) {// 这里过滤掉不是提升卡的操作
				continue;
			}
			Element idcell = cells.get(0);// 第一个单元格中包含有id
			String id = idcell.getElementsByTag("a").get(0).text();
			Element timecell = cells.get(1);// 第二个单元格就是time了
			String time = "";
			Element timespan = timecell.getElementsByTag("span").first();// time有两种，一种在span标签里面
			if (timespan != null) {
				time = timespan.attr("title");// attr用于获取元素的属性值，这个值就是我们要的time
			} else {
				time = timecell.text();// 6天过后的时间将直接被包含在单元格中
			}
			ID.add(id);
			Time.add(time);
		}
	}
	public void kickExpiredData() {
  		  SimpleDateFormat sdfm = new SimpleDateFormat("yyyy-M-d HH:mm");
  		  Date now = new Date();
  		  long validtime = Option.REWARD_PERIOD.getInt() * 24 * 60 * 60 * 1000L;
  		  Date expirydate = new Date(now.getTime() - validtime);
   		 for (int i = 0; i < Time.size(); i++) {
   		     String timeStr = Time.get(i);
    		    if (timeStr == null || timeStr.isEmpty()) {
    		        continue;  // Skip this iteration if the string is null or empty
    		    }
   		     Date date = null;
   		     try {
     		       date = sdfm.parse(timeStr);
    		    } catch (ParseException e) {
   		         e.printStackTrace();
   		         continue;  // Skip this iteration if the date parsing fails
   		     }
   		     if (date.before(expirydate)) {
   		         Time.remove(i);
   		         ID.remove(i);
    		        i--;
      		  }
  		  }
		}

	public void activeReward() {// 主动给玩家发奖励
		for (int i = 0; i < ID.size(); i++) {
			String bbsname = ID.get(i);
			String time = Time.get(i);
			if (!sql.checkTopstate(bbsname, time)) {// 如果这个记录不存在于数据库中
				String uuid = sql.bbsNameCheck(bbsname);
				Poster poster = sql.getPoster(uuid);
				if (uuid != null) {// 这个玩家已经绑定,这时候就可以开始对玩家进行检测了
					OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
					Player olplayer;
					if (player.isOnline()) {// 如果玩家在线
						olplayer = Bukkit.getPlayer(UUID.fromString(uuid));
						if (!olplayer.hasPermission("bbstoper.reward")) {
							continue;// 没有奖励权限的跳过
						}
					} else {// 不在线就跳过
						continue;
					}
					String datenow = new SimpleDateFormat("yyyy-M-dd").format(new Date());
					if (!datenow.equals(poster.getRewardbefore())) {// 上次领奖的日期不是今天，直接将奖励次数清零
						poster.setRewardbefore(datenow);// 奖励日期设置为今天
						poster.setRewardtime(0);
					}
					if (poster.getRewardtime() >= Option.REWARD_TIMES.getInt()) {
						continue;// 如果领奖次数已经大于设定值了，那么跳出循环
					}
					// 这时候就可以给玩家发奖励了
					new Reward(olplayer, this, i).award();
					sql.addTopState(bbsname, time);
					poster.setRewardtime(poster.getRewardtime() + 1);
					sql.updatePoster(poster);// 把poster储存起来
					for (Player p :Bukkit.getOnlinePlayers()) {// 给有奖励权限且能看见此玩家(防止Vanish)的玩家广播
						if (!p.canSee(olplayer)) continue;
						if (!p.hasPermission("bbstoper.reward")) continue;
						p.sendMessage(Message.BROADCAST.getString().replaceAll("%PLAYER%", player.getName()));
					}
				}
			}
		}
	}

	public static void setSQLer(SQLer sql) {
		Crawler.sql = sql;
	}

}
