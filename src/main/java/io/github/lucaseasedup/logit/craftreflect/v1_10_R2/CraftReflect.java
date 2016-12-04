package io.github.lucaseasedup.logit.craftreflect.v1_10_R2;

import io.github.lucaseasedup.logit.craftreflect.v1_10_R2.CraftPlayer;
import org.bukkit.entity.Player;

public final class CraftReflect
        implements io.github.lucaseasedup.logit.craftreflect.CraftReflect
{
    @Override
    public io.github.lucaseasedup.logit.craftreflect.CraftPlayer getCraftPlayer(
            Player player
    )
    {
        return new CraftPlayer(player);
    }
}
