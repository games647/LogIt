package io.github.lucaseasedup.logit.config.observers;

import io.github.lucaseasedup.logit.config.Property;
import io.github.lucaseasedup.logit.config.PropertyObserver;
import io.github.lucaseasedup.logit.util.PlayerUtils;
import org.bukkit.entity.Player;

public final class HideOtherPlayersObserver extends PropertyObserver
{
    @Override
    public void update(Property p)
    {
        if (p.getBoolean())
        {
            for (Player player : PlayerUtils.getOnlinePlayers())
            {
                if (getSessionManager().isSessionAlive(player)
                        || !getCore().isPlayerForcedToLogIn(player))
                {
                    continue;
                }
                
                for (Player otherPlayer : PlayerUtils.getOnlinePlayers())
                {
                    if (otherPlayer == player)
                        continue;
                    
                    otherPlayer.hidePlayer(player);
                    player.hidePlayer(otherPlayer);
                }
            }
        }
        else
        {
            for (Player player : PlayerUtils.getOnlinePlayers())
            {
                if (getSessionManager().isSessionAlive(player)
                        || !getCore().isPlayerForcedToLogIn(player))
                {
                    continue;
                }
                
                for (Player otherPlayer : PlayerUtils.getOnlinePlayers())
                {
                    if (otherPlayer == player)
                        continue;
                    
                    otherPlayer.showPlayer(player);
                    player.showPlayer(otherPlayer);
                }
            }
        }
    }
}
