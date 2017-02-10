package io.github.lucaseasedup.logit.persistence;

import io.github.lucaseasedup.logit.LogItCoreObject;
import io.github.lucaseasedup.logit.util.PlayerUtils;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

@Keys({
    @Key(name = "world", constraint = KeyConstraint.NOT_EMPTY),
    @Key(name = "x", constraint = KeyConstraint.NOT_EMPTY),
    @Key(name = "y", constraint = KeyConstraint.NOT_EMPTY),
    @Key(name = "z", constraint = KeyConstraint.NOT_EMPTY),
    @Key(name = "yaw", constraint = KeyConstraint.NOT_EMPTY),
    @Key(name = "pitch", constraint = KeyConstraint.NOT_EMPTY),
})
public final class LocationSerializer extends LogItCoreObject implements PersistenceSerializer
{
    @Override
    public void serialize(Map<String, String> data, final Player player)
    {
        serialize(data, player.getLocation());
        
        if (player.isOnline())
        {
            final Location waitingRoomLocation = getCore().getWaitingRoomLocation();
            
            player.teleport(waitingRoomLocation);
            
            final int teleportPasses = getConfig("secret.yml")
                    .getInt("locationSerializer.teleportCheck.passes");
            final double dislocationRadius = getConfig("secret.yml")
                    .getDouble("locationSerializer.teleportCheck.dislocationRadius");
            
            // Make sure that no other plugin teleported the player away.
            new BukkitRunnable()
            {
                @Override
                public void run()
                {
                    if (++passes > teleportPasses)
                    {
                        cancel();
                    }
                    else if (!PlayerUtils.isPlayerAt(player, waitingRoomLocation,
                            dislocationRadius, dislocationRadius, dislocationRadius))
                    {
                        player.teleport(waitingRoomLocation);
                    }
                }
                
                /**
                 * Tells how many checks has been done so far.
                 */
                private int passes = 0;
            }.runTaskTimer(getPlugin(), 1L, 1L);
        }
    }
    
    @Override
    public void unserialize(Map<String, String> data, Player player)
    {
        if (player.isOnline())
        {
            org.bukkit.Location location = unserialize(data);
            
            if (location != null)
            {
                player.teleport(location);
            }
        }
    }
    
    private static void serialize(Map<String, String> data, org.bukkit.Location location)
    {
        data.put("world", location.getWorld().getName());
        data.put("x", String.valueOf(location.getX()));
        data.put("y", String.valueOf(location.getY()));
        data.put("z", String.valueOf(location.getZ()));
        data.put("yaw", String.valueOf(location.getYaw()));
        data.put("pitch", String.valueOf(location.getPitch()));
    }
    
    private static org.bukkit.Location unserialize(Map<String, String> data)
    {
        World world = Bukkit.getWorld(data.get("world"));
        
        if (world == null)
            return null;
        
        return new org.bukkit.Location(
                world,
                Double.valueOf(data.get("x")),
                Double.valueOf(data.get("y")),
                Double.valueOf(data.get("z")),
                Float.valueOf(data.get("yaw")),
                Float.valueOf(data.get("pitch"))
        );
    }
    
    @Keys({
        @Key(name = "world", constraint = KeyConstraint.NOT_EMPTY),
        @Key(name = "x", constraint = KeyConstraint.NOT_EMPTY),
        @Key(name = "y", constraint = KeyConstraint.NOT_EMPTY),
        @Key(name = "z", constraint = KeyConstraint.NOT_EMPTY),
        @Key(name = "yaw", constraint = KeyConstraint.NOT_EMPTY),
        @Key(name = "pitch", constraint = KeyConstraint.NOT_EMPTY),
    })
    public static final class PlayerlessLocationSerializer implements PersistenceSerializer
    {
        public PlayerlessLocationSerializer(org.bukkit.Location location)
        {
            if (location == null)
                throw new IllegalArgumentException();
            
            this.location = location;
        }
        
        @Override
        public void serialize(Map<String, String> data, Player player)
        {
            LocationSerializer.serialize(data, location);
        }
        
        @Override
        public void unserialize(Map<String, String> data, Player player)
        {
            location = LocationSerializer.unserialize(data);
        }
        
        public Location getLocation()
        {
            return location;
        }
        
        private Location location;
    }
}
