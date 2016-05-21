package io.github.lucaseasedup.logit.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class PlayerUtils
{
    private PlayerUtils()
    {
    }
    
    public static Player getPlayer(String name)
    {
        if (name == null)
            throw new IllegalArgumentException();
        
        return Bukkit.getPlayerExact(name);
    }

    public static Collection<? extends Player> getOnlinePlayers()
    {
        if (oldPlayerMethod)
        {
            Object onlinePlayersResult;
            try
            {
                onlinePlayersResult = onlinePlayersMethod.invoke(Bukkit.getServer());
                return Arrays.asList((Player[]) onlinePlayersResult);
            } catch (IllegalAccessException | InvocationTargetException ex)
            {
                throw new RuntimeException(ex);
            }
        }

        return Bukkit.getOnlinePlayers();
    }
    
    /**
     * Returns a case-correct player name.
     * 
     * @param name the name of a player.
     * 
     * @return the case-correct player name.
     */
    public static String getPlayerRealName(String name)
    {
        if (name == null)
            throw new IllegalArgumentException();
        
        if (isPlayerOnline(name))
        {
            return getPlayer(name).getName();
        }
        else
        {
            return name;
        }
    }
    
    /**
     * Checks if a player with the given name is online.
     * 
     * @param name the player name.
     * 
     * @return {@code true} if online; {@code false} otherwise.
     */
    public static boolean isPlayerOnline(String name)
    {
        return getPlayer(name) != null;
    }
    
    public static boolean isAnotherPlayerOnline(Player player)
    {
        Player p = getPlayer(player.getName());
        
        if (p == null)
            return false;
        
        return !p.equals(player);
    }
    
    public static String getPlayerIp(Player player)
    {
        if (player.getAddress() == null)
            return null;
        
        return player.getAddress().getAddress().getHostAddress();
    }
    
    /**
     * Checks if the player is within the specified radius of a {@link org.bukkit.Location}.
     * 
     * @param player   a player whose location will be checked.
     * @param location the location which will be compared with the player location.
     * @param radiusX  the maximum radius on X-axis.
     * @param radiusY  the maximum radius on Y-axis.
     * @param radiusZ  the maximum radius on Z-axis.
     * 
     * @return {@code true} if the player is within the radius; {@code false} otherwise.
     */
    public static boolean isPlayerAt(
            Player player, Location location,
            double radiusX, double radiusY, double radiusZ
    )
    {
        Location playerLocation = player.getLocation();
        
        if (playerLocation.getWorld() != location.getWorld())
            return false;
        
        if (Math.abs(playerLocation.getX() - location.getX()) > radiusX)
            return false;

        if (Math.abs(playerLocation.getY() - location.getY()) > radiusY)
            return false;

        if (Math.abs(playerLocation.getZ() - location.getZ()) > radiusZ)
            return false;
        
        return true;
    }

    private static Method onlinePlayersMethod;
    private static boolean oldPlayerMethod;

    static {
        try {
            onlinePlayersMethod = Bukkit.class.getMethod("getOnlinePlayers");
            oldPlayerMethod = !onlinePlayersMethod.getReturnType().isAssignableFrom(Collection.class);
        } catch (NoSuchMethodException | SecurityException ex) {
            //try to fail silently
        }
    }
}
