package io.github.lucaseasedup.logit.config.observers;

import io.github.lucaseasedup.logit.account.Account;
import io.github.lucaseasedup.logit.config.Property;
import io.github.lucaseasedup.logit.config.PropertyObserver;
import io.github.lucaseasedup.logit.persistence.AirBarSerializer;
import io.github.lucaseasedup.logit.persistence.ExperienceSerializer;
import io.github.lucaseasedup.logit.persistence.HealthBarSerializer;
import io.github.lucaseasedup.logit.persistence.HungerBarSerializer;
import io.github.lucaseasedup.logit.persistence.LocationSerializer;
import io.github.lucaseasedup.logit.persistence.PersistenceSerializer;
import io.github.lucaseasedup.logit.util.PlayerUtils;
import java.util.Arrays;
import java.util.logging.Level;
import org.bukkit.entity.Player;

public final class SerializerObserver extends PropertyObserver
{
    @Override
    public void update(Property p)
    {
        if (getAccountManager() == null || getPersistenceManager() == null)
            return;
        
        Class<? extends PersistenceSerializer> clazz = null;
        
        switch (p.getPath())
        {
        case "waitingRoom.enabled":
            clazz = LocationSerializer.class;
            break;
            
        case "forceLogin.obfuscate.air":
            clazz = AirBarSerializer.class;
            break;

        case "forceLogin.obfuscate.health":
            clazz = HealthBarSerializer.class;
            break;
            
        case "forceLogin.obfuscate.experience":
            clazz = ExperienceSerializer.class;
            break;

        case "forceLogin.obfuscate.hunger":
            clazz = HungerBarSerializer.class;
            break;
            
        default:
            break;
        }
        
        if (clazz != null)
        {
            if (p.getBoolean())
            {
                enableSerializer(clazz);
            }
            else
            {
                disableSerializer(clazz);
            }
        }
    }
    
    private void enableSerializer(Class<? extends PersistenceSerializer> clazz)
    {
        for (Player player : PlayerUtils.getOnlinePlayers())
        {
            if (getSessionManager().isSessionAlive(player))
                continue;
            
            Account account = getAccountManager().selectAccount(
                    player.getName(),
                    Arrays.asList(
                            keys().username(),
                            keys().persistence()
                    )
            );
            
            if (account != null)
            {
                getPersistenceManager().serializeUsing(account, player, clazz);
            }
        }
        
        try
        {
            getPersistenceManager().registerSerializer(clazz);
        }
        catch (ReflectiveOperationException ex)
        {
            log(Level.WARNING, "Could not register serializer: "
                    + clazz.getSimpleName(), ex);
        }
    }
    
    private void disableSerializer(Class<? extends PersistenceSerializer> clazz)
    {
        for (Player player : PlayerUtils.getOnlinePlayers())
        {
            Account account = getAccountManager().selectAccount(
                    player.getName(),
                    Arrays.asList(
                            keys().username(),
                            keys().persistence()
                    )
            );
            
            if (account != null)
            {
                getPersistenceManager().unserializeUsing(account, player, clazz);
            }
        }
        
        getPersistenceManager().unregisterSerializer(clazz);
    }
}
