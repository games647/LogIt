package io.github.lucaseasedup.logit.craftreflect.v1_11_R1;

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
    
    private net.minecraft.server.v1_11_R1.EntityPlayer getThis()
    {
        return (net.minecraft.server.v1_11_R1.EntityPlayer) getHolder().get();
    }
}
