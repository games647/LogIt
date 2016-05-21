package io.github.lucaseasedup.logit.message;

import io.github.lucaseasedup.logit.LogItPlugin;
import io.github.lucaseasedup.logit.util.CollectionUtils;
import io.github.lucaseasedup.logit.util.PlayerUtils;
import java.util.Collection;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MessageHelper
{
    private MessageHelper()
    {
    }
    
    /**
     * Returns the translated message associated with the given label.
     * 
     * @param label the message label.
     * 
     * @return the translated message.
     */
    public static String t(String label)
    {
        if (label == null)
            throw new IllegalArgumentException();
        
        return LogItPlugin.getMessage(label);
    }
    
    /**
     * Sends a message to the specified {@code CommandSender}.
     * 
     * <p> If the provided {@code CommandSender} is not a {@code Player},
     * the message will be stripped of colours.
     * 
     * @param sender  the {@code CommandSender} who will receive the message.
     * @param message the message to be sent.
     */
    public static void sendMsg(CommandSender sender, String message)
    {
        if (sender == null)
            throw new IllegalArgumentException();
        
        if (message == null)
            return;
        
        if (sender instanceof Player)
        {
            sender.sendMessage(message);
        }
        else
        {
            sender.sendMessage(ChatColor.stripColor(message));
        }
    }
    
    /**
     * Sends a message to a player with the specified name if they are online.
     * 
     * @param playerName a name of the player who will receive the message.
     * @param message    the message to be sent.
     */
    public static void sendMsg(String playerName, String message)
    {
        if (playerName == null)
            throw new IllegalArgumentException();
        
        if (message == null)
            return;
        
        Player player = Bukkit.getPlayerExact(playerName);
        
        if (player != null)
        {
            sendMsg(player, message);
        }
    }
    
    /**
     * Sends a message to all online players.
     * 
     * @param message the message to be sent.
     */
    public static void broadcastMsg(String message)
    {
        if (message == null)
            return;
        
        for (Player p : PlayerUtils.getOnlinePlayers())
        {
            sendMsg(p, message);
        }
    }
    
    /**
     * Sends a message to all online players with an exception to player names
     * confined in {@code exceptPlayers}.
     * 
     * @param message       the message to be broadcasted.
     * @param exceptPlayers the case-insensitive player names {@code Collection}
     *                      that will omitted in the broadcasting.
     */
    public static void broadcastMsgExcept(
            String message, Collection<String> exceptPlayers
    )
    {
        if (message == null)
            return;
        
        if (exceptPlayers == null)
            throw new IllegalArgumentException();
        
        for (Player p : PlayerUtils.getOnlinePlayers())
        {
            if (!CollectionUtils.containsIgnoreCase(p.getName(), exceptPlayers))
            {
                sendMsg(p, message);
            }
        }
    }
}
