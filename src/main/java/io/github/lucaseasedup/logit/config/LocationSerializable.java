package io.github.lucaseasedup.logit.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

@SerializableAs("Location")
public final class LocationSerializable
        implements Cloneable, ConfigurationSerializable
{
    public LocationSerializable(
            String world, double x, double y, double z, float yaw, float pitch
    )
    {
        if (world == null)
            throw new IllegalArgumentException();
        
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }
    
    public LocationSerializable(String world, double x, double y, double z)
    {
        this(world, x, y, z, 0, 0);
    }
    
    @Override
    public LocationSerializable clone()
    {
        try
        {
            return (LocationSerializable) super.clone();
        }
        catch (CloneNotSupportedException ex)
        {
            throw new Error(ex);
        }
    }
    
    @Override
    public String toString()
    {
        return "world: " + world
                + "; x: " + new BigDecimal(x).setScale(2, RoundingMode.HALF_UP)
                + "; y: " + new BigDecimal(y).setScale(2, RoundingMode.HALF_UP)
                + "; z: " + new BigDecimal(z).setScale(2, RoundingMode.HALF_UP)
                + "; yaw: " + new BigDecimal(yaw).setScale(2, RoundingMode.HALF_UP)
                + "; pitch: " + new BigDecimal(pitch).setScale(2, RoundingMode.HALF_UP);
    }
    
    public org.bukkit.Location toBukkitLocation()
    {
        World worldObj = Bukkit.getWorld(world);
        
        if (worldObj == null) {
            throw new IllegalStateException("Cannot find world name of: " + world);
        }
        
        return new org.bukkit.Location(worldObj, x, y, z, yaw, pitch);
    }
    
    @Override
    public Map<String, Object> serialize()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        
        result.put("world", world);
        result.put("x", x);
        result.put("y", y);
        result.put("z", z);
        result.put("yaw", yaw);
        result.put("pitch", pitch);
        
        return result;
    }
    
    public static LocationSerializable deserialize(Map<String, Object> args)
    {
        String world = null;
        double x = 0;
        double y = 0;
        double z = 0;
        float yaw = 0;
        float pitch = 0;
        
        if (args.containsKey("world"))
        {
            world = (String) args.get("world");
        }
        
        if (args.containsKey("x"))
        {
            x = (Double) args.get("x");
        }
        
        if (args.containsKey("y"))
        {
            y = (Double) args.get("y");
        }
        
        if (args.containsKey("z"))
        {
            z = (Double) args.get("z");
        }
        
        if (args.containsKey("yaw"))
        {
            yaw = ((Double) args.get("yaw")).floatValue();
        }
        
        if (args.containsKey("pitch"))
        {
            pitch = ((Double) args.get("pitch")).floatValue();
        }
        
        return new LocationSerializable(world, x, y, z, yaw, pitch);
    }
    
    public String getWorld()
    {
        return world;
    }
    
    public double getX()
    {
        return x;
    }
    
    public double getY()
    {
        return y;
    }
    
    public double getZ()
    {
        return z;
    }
    
    public float getYaw()
    {
        return yaw;
    }
    
    public float getPitch()
    {
        return pitch;
    }
    
    public void setWorld(String world)
    {
        this.world = world;
    }
    
    public void setX(double x)
    {
        this.x = x;
    }
    
    public void setY(double y)
    {
        this.y = y;
    }
    
    public void setZ(double z)
    {
        this.z = z;
    }
    
    public void setYaw(float yaw)
    {
        this.yaw = yaw;
    }
    
    public void setPitch(float pitch)
    {
        this.pitch = pitch;
    }
    
    public static LocationSerializable fromBukkitLocation(org.bukkit.Location location)
    {
        return new LocationSerializable(location.getWorld().getName(),
                            location.getX(),
                            location.getY(),
                            location.getZ(),
                            location.getYaw(),
                            location.getPitch());
    }
    
    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
}
