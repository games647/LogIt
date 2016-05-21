package org.mcsg.double0negative.tabapi;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ConnectionSide;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketAdapter.AdapterParameteters;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import io.github.lucaseasedup.logit.LogItCore;
import io.github.lucaseasedup.logit.util.PlayerUtils;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Provides a simple interface for adding custom text to
 * display on the Minecraft tab menu on a player/plugin basis.
 * 
 * @author     Double0negative
 * @modifiedby LucasEU
 */
public final class TabAPI implements Listener
{
    public void onEnable()
    {
        protocolManager = ProtocolLibrary.getProtocolManager();
        
        Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
        
        AdapterParameteters params = new AdapterParameteters();
        params.plugin(plugin);
        params.connectionSide(ConnectionSide.SERVER_SIDE);
        params.listenerPriority(ListenerPriority.NORMAL);
        params.types(PacketType.Play.Server.PLAYER_INFO);
        
        packetListener = new PacketAdapter(params)
        {
            @Override
            public void onPacketSending(PacketEvent event)
            {
                if (!event.getPacketType().equals(PacketType.Play.Server.PLAYER_INFO))
                    return;
                
                PacketContainer container = event.getPacket();
                String string = container.getStrings().read(0);
                
                if (string.startsWith("$"))
                {
                    container.getStrings().write(0, string.substring(1));
                    
                    event.setPacket(container);
                }
                else
                {
                    event.setCancelled(true);
                }
            }
        };
        
        protocolManager.addPacketListener(packetListener);
    }
    
    public void onDisable()
    {
        if (protocolManager != null)
        {
            protocolManager.removePacketListener(packetListener);
            protocolManager = null;
        }
        
        packetListener = null;
        
        if (tabObjects != null)
        {
            tabObjects.clear();
            tabObjects = null;
        }
        
        if (tabHolders != null)
        {
            tabHolders.clear();
            tabHolders = null;
        }
    }
    
    private void addPacket(Player player, String msg, boolean b, int ping)
    {
        if (player == null)
            throw new IllegalArgumentException();
        
        PacketContainer message =
                protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);
        
        message.getStrings().write(0, "$" + msg);
        message.getBooleans().write(0, b);
        message.getIntegers().write(0, ping);
        
        List<PacketContainer> packets = cachedPackets.get(player);
        
        if (packets == null)
        {
            packets = new ArrayList<>();
            
            cachedPackets.put(player, packets);
        }
        
        packets.add(message);
    }
    
    private void flushPackets(final Player player, final TabHolder tabCopy)
    {
        if (player == null)
            throw new IllegalArgumentException();
        
        final List<PacketContainer> packets = cachedPackets.get(player);
        Integer taskId = updateSchedules.get(player);
        
        // prevent flickering
        if (taskId != null)
        {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        
        taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
        {
            @Override
            public void run()
            {
                if (player.isOnline() && packets != null)
                {
                    for (PacketContainer packet : packets)
                    {
                        try
                        {
                            protocolManager.sendServerPacket(player, packet);
                        }
                        catch (InvocationTargetException ex)
                        {
                            ex.printStackTrace();
                        }
                    }
                }
                
                // we set this only if we really finally flush it (which is just now)
                if (tabCopy != null)
                {
                    tabHolders.put(player.getName(), tabCopy);
                }
                
                // we're done, no need to cancel this one on next run
                updateSchedules.remove(player);
            }
        }, 5L);
        
        // let's keep a reference to be able to cancel this (see above)
        updateSchedules.put(player, taskId);
        
        cachedPackets.remove(player);
    }
    
    private TabObject getTab(Player player)
    {
        TabObject tabObj = tabObjects.get(player.getName());
        
        if (tabObj == null)
        {
            tabObj = new TabObject();
            
            tabObjects.put(player.getName(), tabObj);
        }
        
        return tabObj;
    }
    
    /**
     * Sets priority for a player's tab list.
     * 
     * -2 = no longer active, remove
     * -1 = background, only show if nothing else is there
     *  0 = normal
     *  1 = high priority
     *  2 = always show, only use if MUST show
     * 
     * @param player
     * @param priority
     */
    public void setPriority(Player player, int priority)
    {
        getTab(player).setPriority(this, priority);
    }
    
    /**
     * Set the tab for a player.
     * 
     * <p>If the plugin the tab is being set from does not have a priority,
     * it will automatically be give a base priority of 0.
     * 
     * @param player
     * @param x
     * @param y
     * @param msg
     * @param ping
     */
    public void setTabString(Player player, int x, int y, String msg, int ping)
    {
        if (!player.isOnline())
            return;
        
        TabObject tabObj = getTab(player);
        
        tabObj.setTab(this, x, y, msg, ping);
        
        tabObjects.put(player.getName(), tabObj);
    }
    
    public void setTabString(Player player, int x, int y, String msg)
    {
        setTabString(player, x, y, msg, 0);
    }
    
    /**
     * Updates a players tab.
     * 
     * <p>A tab will be updated with the tab from the highest priority plugin.
     * 
     * @param player
     */
    public void updatePlayer(Player player)
    {
        if (!player.isOnline())
            return;
        
        TabObject tabObj = tabObjects.get(player.getName());
        
        if (tabObj == null)
            return;
        
        TabHolder holder = tabObj.getTab();
        
        if (holder == null)
            return;
        
        // need to clear the tab first
        clearTab(player);
        
        for (int i = 0; i < holder.maxv; i++)
        {
            for (int j = 0; j < holder.maxh; j++)
            {
                String msg = holder.tabs[j][i];
                
                if (msg == null)
                    continue;
                
                String truncatedMsg = msg.substring(0, Math.min(msg.length(), 16));
                int ping = holder.tabPings[j][i];
                
                addPacket(player, truncatedMsg, true, ping);
            }
        }
        
        flushPackets(player, holder.copy());
    }
    
    /**
     * Clear a players tab menu.
     * 
     * @param player
     */
    public void clearTab(Player player)
    {
        if (!player.isOnline())
            return;
        
        TabHolder holder = tabHolders.get(player.getName());
        
        if (holder == null)
            return;
        
        for (String[] row : holder.tabs)
        {
            for (String msg : row)
            {
                if (msg == null)
                    continue;
                
                addPacket(player, msg.substring(0, Math.min(msg.length(), 16)), false, 0);
            }
        }
    }
    
    public void updateAll()
    {
        for (Player player : PlayerUtils.getOnlinePlayers())
        {
            updatePlayer(player);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPlayerQuit(PlayerQuitEvent event)
    {
        Player player = event.getPlayer();
        
        tabObjects.remove(player.getName());
        tabHolders.remove(player.getName());
        cachedPackets.remove(player);
        
        if (updateSchedules.containsKey(player))
        {
            int taskId = updateSchedules.get(player);
            
            Bukkit.getScheduler().cancelTask(taskId);
        }
        
        updateSchedules.remove(player);
    }
    
    public int getVertSize()
    {
        return vertTabSize;
    }
    
    public int getHorizSize()
    {
        return horizTabSize;
    }
    
    private static final Plugin plugin = LogItCore.getInstance().getPlugin();
    
    private static final int horizTabSize = 3;
    private static final int vertTabSize = 20;
    
    private ProtocolManager protocolManager;
    private PacketListener packetListener;
    
    private Map<String, TabObject> tabObjects = new HashMap<>();
    private Map<String, TabHolder> tabHolders = new HashMap<>();
    
    private final Map<Player, List<PacketContainer>> cachedPackets = new HashMap<>();
    private final Map<Player, Integer> updateSchedules = new HashMap<>();
}
