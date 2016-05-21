package io.github.lucaseasedup.logit.craftreflect.v1_8_R3;

public final class EntityPlayer
        extends io.github.lucaseasedup.logit.craftreflect.EntityPlayer
{
    protected EntityPlayer(Object o)
    {
        super(o);
    }
    
    @Override
    public int getPing()
    {
        return getThis().ping;
    }
    
    private net.minecraft.server.v1_7_R4.EntityPlayer getThis()
    {
        return (net.minecraft.server.v1_7_R4.EntityPlayer) getHolder().get();
    }
}
