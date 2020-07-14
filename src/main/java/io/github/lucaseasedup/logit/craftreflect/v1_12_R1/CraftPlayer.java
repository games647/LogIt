package io.github.lucaseasedup.logit.craftreflect.v1_12_R1;

import org.bukkit.entity.Player;

public final class CraftPlayer
        extends io.github.lucaseasedup.logit.craftreflect.CraftPlayer
{
    protected CraftPlayer(Player player)
    {
        super(player);
    }
    
    @Override
    public EntityPlayer getHandle()
    {
        return new EntityPlayer(getThis().getHandle());
    }
    
    private org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer getThis()
    {
        return (org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer) getHolder().get();
    }
}