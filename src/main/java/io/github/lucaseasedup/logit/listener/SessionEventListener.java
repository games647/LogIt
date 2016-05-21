package io.github.lucaseasedup.logit.listener;

import io.github.lucaseasedup.logit.LogItCoreObject;
import io.github.lucaseasedup.logit.account.Account;
import io.github.lucaseasedup.logit.config.TimeUnit;
import io.github.lucaseasedup.logit.session.SessionEndEvent;
import io.github.lucaseasedup.logit.session.SessionStartEvent;
import io.github.lucaseasedup.logit.util.PlayerUtils;
import java.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

public final class SessionEventListener extends LogItCoreObject
        implements Listener
{
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onStart(SessionStartEvent event)
    {
        String username = event.getUsername();
        Account account = getAccountManager().selectAccount(username, Arrays.asList(
                keys().username(),
                keys().persistence()
        ));
        
        if (account != null)
        {
            account.setLastActiveDate(System.currentTimeMillis() / 1000L);
        }
        
        if (!PlayerUtils.isPlayerOnline(username))
            return;
        
        final Player player = Bukkit.getPlayerExact(username);
        
        new BukkitRunnable()
        {
            @Override
            public void run()
            {
                if (getConfig("config.yml").getBoolean("groups.enabled"))
                {
                    getCore().updatePlayerGroup(player);
                }
                
                if (getCore().isPlayerForcedToLogIn(player)
                        && !getConfig("config.yml").getBoolean("messages.join.hide"))
                {
                    getMessageDispatcher().broadcastJoinMessage(player);
                }
            }
        }.runTaskLater(getPlugin(), 1L);
        
        if (getCore().isPlayerForcedToLogIn(player))
        {
            if (account != null)
            {
                getPersistenceManager().unserialize(account, player);
            }
            
            if (getConfig("config.yml").getBoolean("forceLogin.hideOtherPlayers"))
            {
                for (Player onlinePlayer : PlayerUtils.getOnlinePlayers())
                {
                    if (onlinePlayer == player)
                        continue;
                    
                    if (!getSessionManager().isSessionAlive(onlinePlayer)
                            && getCore().isPlayerForcedToLogIn(onlinePlayer))
                    {
                        continue;
                    }
                    
                    onlinePlayer.showPlayer(player);
                    player.showPlayer(onlinePlayer);
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onEnd(SessionEndEvent event)
    {
        String username = event.getUsername();
        Account account = getAccountManager().selectAccount(username, Arrays.asList(
                keys().username(),
                keys().persistence()
        ));
        
        if (account != null)
        {
            account.setLastActiveDate(System.currentTimeMillis() / 1000L);
        }
        
        if (!PlayerUtils.isPlayerOnline(username))
            return;
        
        final Player player = Bukkit.getPlayerExact(username);
        
        new BukkitRunnable()
        {
            @Override
            public void run()
            {
                if (getConfig("config.yml").getBoolean("groups.enabled"))
                {
                    getCore().updatePlayerGroup(player);
                }
                
                if (getCore().isPlayerForcedToLogIn(player)
                        && !getConfig("config.yml").getBoolean("messages.quit.hide"))
                {
                    getMessageDispatcher().broadcastQuitMessage(player);
                }
            }
        }.runTaskLater(getPlugin(), 1L);
        
        if (getCore().isPlayerForcedToLogIn(player))
        {
            if (account != null)
            {
                getPersistenceManager().serialize(account, player);
            }
            
            if (getConfig("config.yml").getBoolean("forceLogin.hideOtherPlayers"))
            {
                for (Player onlinePlayer : PlayerUtils.getOnlinePlayers())
                {
                    if (onlinePlayer == player)
                        continue;
                    
                    onlinePlayer.hidePlayer(player);
                    player.hidePlayer(onlinePlayer);
                }
            }
            
            if (getConfig("config.yml").getBoolean("forceLogin.periodicalPrompt.enabled"))
            {
                long promptPeriod = getConfig("config.yml")
                        .getTime("forceLogin.periodicalPrompt.period", TimeUnit.TICKS);
                
                getMessageDispatcher().dispatchForceLoginPrompter(
                        player, promptPeriod, promptPeriod
                );
            }
            
            event.getSession().setLastForceLoginLocation(player.getLocation());
        }
    }
}
