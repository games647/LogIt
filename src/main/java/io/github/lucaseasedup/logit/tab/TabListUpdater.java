package io.github.lucaseasedup.logit.tab;

import io.github.lucaseasedup.logit.LogItCoreObject;
import io.github.lucaseasedup.logit.common.Wrapper;
import io.github.lucaseasedup.logit.config.TimeUnit;
import io.github.lucaseasedup.logit.craftreflect.CraftPlayer;
import io.github.lucaseasedup.logit.craftreflect.CraftReflect;
import io.github.lucaseasedup.logit.craftreflect.EntityPlayer;
import io.github.lucaseasedup.logit.session.SessionStartEvent;
import io.github.lucaseasedup.logit.util.PlayerUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.mcsg.double0negative.tabapi.TabAPI;

public final class TabListUpdater extends LogItCoreObject
        implements Runnable, Listener
{
    public TabListUpdater(Wrapper<TabAPI> tabApiWrapper, CraftReflect craftReflect)
    {
        if (tabApiWrapper == null)
            throw new IllegalArgumentException();
        
        this.tabApiWrapper = tabApiWrapper;
        this.craftReflect = craftReflect;
    }
    
    @Override
    public void run()
    {
        updateAllTabLists();
    }
    
    public void updateAllTabLists()
    {
        for (Player player : PlayerUtils.getOnlinePlayers())
        {
            updateTabList(player);
        }
    }
    
    private void updateTabList(Player player)
    {
        if (player == null)
            throw new IllegalArgumentException();
        
        if (getTabApi() == null)
            return;
        
        getTabApi().clearTab(player);
        
        int horizSize = getTabApi().getHorizSize();
        int vertSize = getTabApi().getVertSize();
        int i = 0;
        int j = 0;
        
        for (Player p : PlayerUtils.getOnlinePlayers())
        {
            if (!getSessionManager().isSessionAlive(p) && !p.equals(player)
                    && getConfig("config.yml").getBoolean("forceLogin.hideFromTabList"))
            {
                continue;
            }
            
            int ping;
            
            if (craftReflect == null)
            {
                ping = 0;
            }
            else
            {
                CraftPlayer craftPlayer = craftReflect.getCraftPlayer(p);
                EntityPlayer entityPlayer = craftPlayer.getHandle();
                
                ping = entityPlayer.getPing();
            }
            
            getTabApi().setTabString(player, j, i, p.getPlayerListName(), ping);
            
            i++;
            
            if (i >= horizSize)
            {
                i = 0;
                j++;
            }
            
            if (j >= vertSize)
            {
                break;
            }
        }
        
        getTabApi().updatePlayer(player);
        getTabApi().setPriority(player, 1);
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    private void onPlayerJoin(PlayerJoinEvent event)
    {
        new BukkitRunnable()
        {
            @Override
            public void run()
            {
                updateAllTabLists();
            }
        }.runTaskLater(getPlugin(), 1L);
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    private void onPlayerQuit(PlayerQuitEvent event)
    {
        new BukkitRunnable()
        {
            @Override
            public void run()
            {
                updateAllTabLists();
            }
        }.runTaskLater(getPlugin(), 1L);
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onSessionStart(SessionStartEvent event)
    {
        new BukkitRunnable()
        {
            @Override
            public void run()
            {
                updateAllTabLists();
            }
        }.runTaskLater(getPlugin(), 1L);
    }
    
    private TabAPI getTabApi()
    {
        return tabApiWrapper.get();
    }
    
    /**
     * Recommended task period of {@code TabListUpdater} running as a Bukkit task.
     */
    public static final long TASK_PERIOD = TimeUnit.SECONDS.convertTo(2, TimeUnit.TICKS);
    
    private Wrapper<TabAPI> tabApiWrapper;
    private final CraftReflect craftReflect;
}
