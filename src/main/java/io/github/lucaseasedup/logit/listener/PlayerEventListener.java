package io.github.lucaseasedup.logit.listener;

import static io.github.lucaseasedup.logit.message.MessageHelper.sendMsg;
import static io.github.lucaseasedup.logit.message.MessageHelper.t;
import io.github.lucaseasedup.logit.LogItCoreObject;
import io.github.lucaseasedup.logit.account.Account;
import io.github.lucaseasedup.logit.config.TimeUnit;
import io.github.lucaseasedup.logit.hooks.BukkitSmerfHook;
import io.github.lucaseasedup.logit.hooks.EssentialsHook;
import io.github.lucaseasedup.logit.hooks.VanishNoPacketHook;
import io.github.lucaseasedup.logit.logging.timing.PlayerJoinTiming;
import io.github.lucaseasedup.logit.logging.timing.PlayerLoginTiming;
import io.github.lucaseasedup.logit.message.JoinMessageGenerator;
import io.github.lucaseasedup.logit.message.QuitMessageGenerator;
import io.github.lucaseasedup.logit.persistence.LocationSerializer;
import io.github.lucaseasedup.logit.session.Session;
import io.github.lucaseasedup.logit.storage.Infix;
import io.github.lucaseasedup.logit.storage.SelectorBinary;
import io.github.lucaseasedup.logit.storage.SelectorCondition;
import io.github.lucaseasedup.logit.storage.SelectorNegation;
import io.github.lucaseasedup.logit.util.BlockUtils;
import io.github.lucaseasedup.logit.util.CollectionUtils;
import io.github.lucaseasedup.logit.util.PlayerUtils;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.scheduler.BukkitRunnable;

public final class PlayerEventListener extends LogItCoreObject
        implements Listener
{
    @EventHandler(priority = EventPriority.NORMAL)
    private void onLogin(final PlayerLoginEvent event)
    {
        if (event.getResult() != Result.ALLOWED)
            return;
        
        Player player = event.getPlayer();
        
        onLogin(player, event.getAddress(), new PlayerKicker()
        {
            @Override
            public void kick(Player player, String message)
            {
                event.disallow(Result.KICK_OTHER, message);
            }
        });
    }
    
    public void onLogin(Player player, InetAddress address, PlayerKicker kicker)
    {
        if (player == null || address == null || kicker == null)
            throw new IllegalArgumentException();
        
        String username = player.getName().toLowerCase();
        
        int minUsernameLength = getConfig("secret.yml")
                .getInt("username.minLength");
        int maxUsernameLength = getConfig("secret.yml")
                .getInt("username.maxLength");
        
        if (usernamePattern == null)
        {
            usernamePattern = Pattern.compile(
                    getConfig("secret.yml").getString("username.regex")
            );
        }
        
        if (StringUtils.isBlank(username))
        {
            kicker.kick(player, t("usernameBlank"));
        }
        else if (username.length() < minUsernameLength)
        {
            kicker.kick(player, t("usernameTooShort")
                    .replace("{0}", String.valueOf(minUsernameLength)));
        }
        else if (username.length() > maxUsernameLength)
        {
            kicker.kick(player, t("usernameTooLong")
                    .replace("{0}", String.valueOf(maxUsernameLength)));
        }
        else if (!usernamePattern.matcher(player.getName()).matches())
        {
            kicker.kick(player, t("usernameInvalid"));
        }
        else if (CollectionUtils.containsIgnoreCase(username,
                getConfig("config.yml").getStringList("prohibitedUsernames")))
        {
            kicker.kick(player, t("usernameProhibited"));
        }
        else if (PlayerUtils.isAnotherPlayerOnline(player))
        {
            kicker.kick(player, t("usernameAlreadyUsed"));
        }
        else
        {
            onValidLogin(player, address, kicker);
        }
    }
    
    private void onValidLogin(
            Player player, InetAddress address, PlayerKicker kicker
    )
    {
        if (player == null || address == null || kicker == null)
            throw new IllegalArgumentException();
        
        PlayerLoginTiming timing = new PlayerLoginTiming();
        timing.start();
        
        String username = player.getName().toLowerCase();
        
        // =======================================
        timing.startSelectAccount();
        
        List<String> keys;
        
        if (getConfig("secret.yml").getBoolean("fullLoginSelect"))
        {
            keys = keys().getNames();
        }
        else
        {
            keys = Arrays.asList(
                    keys().username(),
                    keys().uuid(), // for onJoin()
                    keys().login_session(),
                    keys().is_locked(),
                    keys().display_name(),
                    keys().persistence()
            );
        }
        
        Account account = getAccountManager().selectAccount(username, keys);
        
        timing.endSelectAccount();
        // =======================================
        
        if (account != null)
        {
            String displayName = account.getDisplayName();
            boolean usernameCaseMismatchKick = getConfig("config.yml")
                    .getBoolean("usernameCaseMismatch.kick");
            
            if (StringUtils.isBlank(displayName))
            {
                account.setDisplayName(player.getName());
            }
            else if (!player.getName().equals(displayName)
                    && usernameCaseMismatchKick)
            {
                kicker.kick(player, t("usernameCaseMismatch.kick")
                        .replace("{0}", displayName));
                
                return;
            }
            
            if (account.isLocked())
            {
                kicker.kick(player, t("acclock.success.self"));
                
                return;
            }
        }
        
        if (getConfig("config.yml").getBoolean("kickUnregistered")
                && account == null)
        {
            kicker.kick(player, t("kickUnregistered"));
        }
        else
        {
            int freeSlots =
                    Bukkit.getMaxPlayers() - PlayerUtils.getOnlinePlayers().size();
            List<String> reserveForPlayers = getConfig("config.yml")
                    .getStringList("reserveSlots.forPlayers");
            int reservedSlots = 0;
            
            // Calculate how many players for which slots should be reserved
            // are online.
            for (Player p : PlayerUtils.getOnlinePlayers())
            {
                if (CollectionUtils.containsIgnoreCase(
                        p.getName(), reserveForPlayers
                ))
                {
                    reservedSlots++;
                }
            }
            
            int maxReservedSlots = getConfig("config.yml")
                    .getInt("reserveSlots.amount");
            int unusedReservedSlots = maxReservedSlots - reservedSlots;
            int actualFreeSlots = freeSlots - unusedReservedSlots;
            
            if (actualFreeSlots <= 0 && !CollectionUtils.containsIgnoreCase(
                    username, reserveForPlayers
            ))
            {
                kicker.kick(player, t("noSlotsFree"));
            }
        }
        
        timing.end();
        
        if (getConfig("secret.yml").getBoolean("timings.enabled"))
        {
            getCore().saveTiming(timing);
        }
    }
    
    @EventHandler(priority = EventPriority.LOW)
    private void onJoin(final PlayerJoinEvent event)
    {
        Player player = event.getPlayer();
        
        onJoin(player, new JoinMessage()
        {
            @Override
            public void set(String joinMessage)
            {
                event.setJoinMessage(joinMessage);
            }
        });
    }
    
    public void onJoin(final Player player, JoinMessage joinMessage)
    {
        if (player == null || joinMessage == null)
            throw new IllegalArgumentException();
        
        PlayerJoinTiming timing = new PlayerJoinTiming();
        timing.start();
        
        String username = player.getName().toLowerCase();
        UUID uuid = player.getUniqueId();
        
        joinMessage.set(null);
        
        // =======================================
        timing.startCreateSession();
        
        if (getSessionManager().getSession(player) == null)
        {
            getSessionManager().createSession(player);
        }
        
        timing.endCreateSession();
        // =======================================
        timing.startSelectAccount();
        
        List<String> joinQueryKeys = Arrays.asList(
                keys().username(),
                keys().uuid(),
                keys().login_session(),
                keys().display_name(),
                keys().persistence()
        );
        Account account = getAccountManager().selectAccount(
                username, joinQueryKeys
        );
        
        timing.endSelectAccount();
        // =======================================
        
        if (account == null)
        {
            // =======================================
            timing.startUuidMatching();
            
            List<Account> uuidMatchedAccounts = getAccountManager().selectAccounts(
                    Arrays.asList(
                            keys().username(),
                            keys().uuid()
                    ),
                    new SelectorBinary(
                            new SelectorNegation(new SelectorCondition(
                                    keys().username(),
                                    Infix.CONTAINS,
                                    "$"
                            )),
                            Infix.AND,
                            new SelectorCondition(
                                    keys().uuid(),
                                    Infix.EQUALS,
                                    uuid.toString()
                            )
                    )
            );
            
            if (uuidMatchedAccounts != null && !uuidMatchedAccounts.isEmpty())
            {
                Account uuidMatchedAccount = uuidMatchedAccounts.get(0);
                String uuidMatchedUsername = uuidMatchedAccount.getUsername();
                
                if (!uuidMatchedUsername.equalsIgnoreCase(username))
                {
                    getAccountManager().renameAccount(
                            uuidMatchedUsername, username
                    );
                    
                    // Load the new account.
                    account = getAccountManager().selectAccount(
                            username, joinQueryKeys
                    );
                }
            }
            
            timing.endUuidMatching();
            // =======================================
            
            if (getConfig("config.yml").getBoolean("waitingRoom.enabled"))
            {
                player.teleport(getCore().getWaitingRoomLocation());
            }
        }
        else
        {
            if (StringUtils.isBlank(account.getUuid())
                    || !account.getUuid().equals(uuid))
            {
                account.setUuid(uuid);
            }
            
            long validnessTime = getConfig("config.yml")
                    .getTime("loginSessions.validnessTime", TimeUnit.SECONDS);
            String ip = PlayerUtils.getPlayerIp(player);
            
            if (getConfig("config.yml").getBoolean("loginSessions.enabled")
                    && validnessTime > 0 && ip != null)
            {
                String loginSession = account.getLoginSession();
                
                if (!StringUtils.isBlank(loginSession))
                {
                    String[] loginSplit = loginSession.split(";");
                    
                    if (loginSplit.length == 2)
                    {
                        String loginIp = loginSplit[0];
                        int loginTime = Integer.parseInt(loginSplit[1]);
                        int currentTime = (int) (System.currentTimeMillis() / 1000L);
                        
                        if (ip.equals(loginIp)
                                && currentTime - loginTime < validnessTime
                                && !getSessionManager().isSessionAlive(username))
                        {
                            getSessionManager().startSession(player);
                        }
                    }
                }
            }
            
            String displayName = account.getDisplayName();
            
            if (!StringUtils.isBlank(displayName)
                    && !player.getName().equals(displayName)
                    && getConfig("config.yml").getBoolean("usernameCaseMismatch.warning"))
            {
                getMessageDispatcher().dispatchMessage(player,
                        t("usernameCaseMismatch.warning")
                                .replace("{0}", displayName), 4L);
            }
            
            boolean premiumTakeoverEnabled = getConfig("config.yml")
                    .getBoolean("premiumTakeover.enabled");
            String promptOn = getConfig("config.yml")
                    .getString("premiumTakeover.promptOn");
            
            if (premiumTakeoverEnabled && promptOn.equals("join")
                    && BukkitSmerfHook.isPremium(player))
            {
                new BukkitRunnable()
                {
                    @Override
                    public void run()
                    {
                        if (!getSessionManager().isSessionAlive(player))
                        {
                            sendMsg(player, t("takeover.prompt"));
                        }
                    }
                }.runTaskLater(getPlugin(), 140L);
            }
        }
        
        if (getSessionManager().isSessionAlive(player)
                || !getCore().isPlayerForcedToLogIn(player))
        {
            if (getConfig("config.yml").getBoolean("forceLogin.hideOtherPlayers"))
            {
                for (Player onlinePlayer : PlayerUtils.getOnlinePlayers())
                {
                    if (onlinePlayer == player)
                        continue;

                    if (getSessionManager().isSessionAlive(onlinePlayer)
                            || !getCore().isPlayerForcedToLogIn(onlinePlayer))
                    {
                        continue;
                    }
                    
                    onlinePlayer.hidePlayer(player);
                    player.hidePlayer(onlinePlayer);
                }
            }
            
            if (!getConfig("config.yml").getBoolean("messages.join.hide")
                    && !VanishNoPacketHook.isVanished(player)
                    && !EssentialsHook.isVanished(player))
            {
                joinMessage.set(JoinMessageGenerator.generate(player,
                        getConfig("config.yml").getBoolean("messages.join.showWorld")));
            }
        }
        else
        {
            if (account != null)
            {
                // =======================================
                timing.startSerialize();
                
                getCore().getPersistenceManager().serialize(account, player);
                
                timing.endSerialize();
                // =======================================
            }
            
            if (getConfig("config.yml").getBoolean("forceLogin.hideOtherPlayers"))
            {
                for (Player onlinePlayer : PlayerUtils.getOnlinePlayers())
                {
                    if (onlinePlayer == player)
                        continue;
                    
                    onlinePlayer.hidePlayer(player);
                    player.hidePlayer(onlinePlayer);
                }
            }
            
            if (!getConfig("config.yml").getBoolean("waitingRoom.enabled"))
            {
                // =======================================
                timing.startSafeLocation();
                
                Location playerLocation = player.getLocation();
                Block nearestBlockBelow = BlockUtils.getNearestBlockBelow(playerLocation);
                
                if (nearestBlockBelow != null
                        && nearestBlockBelow.getType() == Material.PORTAL)
                {
                    playerLocation = BlockUtils.getNearestSafeSpace(playerLocation, 1000);
                    
                    if (playerLocation != null)
                    {
                        player.teleport(playerLocation);
                    }
                }
                
                timing.endSafeLocation();
                // =======================================
            }
            
            long promptPeriod = getConfig("config.yml")
                    .getTime("forceLogin.periodicalPrompt.period", TimeUnit.TICKS);
            boolean promptOnJoin = getConfig("config.yml")
                    .getBoolean("forceLogin.promptOn.join");
            boolean periodicalPrompt = getConfig("config.yml")
                    .getBoolean("forceLogin.periodicalPrompt.enabled");
            
            if (promptOnJoin)
            {
                if (periodicalPrompt)
                {
                    long promptDelay = TimeUnit.SECONDS
                            .convertTo(1, TimeUnit.TICKS);
                    
                    getMessageDispatcher().dispatchForceLoginPrompter(
                            player,
                            promptDelay,
                            promptPeriod
                    );
                }
                else
                {
                    getMessageDispatcher().dispatchForceLoginPrompter(
                            player,
                            5L
                    );
                }
            }
            else if (periodicalPrompt)
            {
                long promptDelay = TimeUnit.SECONDS
                        .convertTo(1, TimeUnit.TICKS);
                
                getMessageDispatcher().dispatchForceLoginPrompter(
                        player,
                        promptDelay + promptPeriod,
                        promptPeriod
                );
            }
        }
        
        if (player.isDead())
        {
            playersDeadOnJoin.add(player);
        }
        
        getSessionManager().getSession(player)
                .setLastForceLoginLocation(player.getLocation());
        
        if (getConfig("config.yml").getBoolean("groups.enabled"))
        {
            getCore().updatePlayerGroup(player);
        }
        
        timing.end();
        
        if (getConfig("secret.yml").getBoolean("timings.enabled"))
        {
            getCore().saveTiming(timing);
        }
    }
    
    @EventHandler(priority = EventPriority.LOW)
    private void onQuit(final PlayerQuitEvent event)
    {
        Player player = event.getPlayer();
        
        onQuit(player, new QuitMessage()
        {
            @Override
            public void set(String quitMessage)
            {
                event.setQuitMessage(quitMessage);
            }
        });
    }
    
    public void onQuit(Player player, QuitMessage quitMessage)
    {
        if (player == null || quitMessage == null)
            throw new IllegalArgumentException();
        
        playersDeadOnJoin.remove(player);
        
        Account account = getAccountManager().selectAccount(
                player.getName(),
                Arrays.asList(
                        keys().username(),
                        keys().persistence()
                )
        );
        
        if (account != null)
        {
            getCore().getPersistenceManager().unserialize(account, player);
        }
        
        if (!getConfig("config.yml").getBoolean("messages.quit.hide")
                && (!getCore().isPlayerForcedToLogIn(player)
                        || getSessionManager().isSessionAlive(player))
                && !VanishNoPacketHook.isVanished(player)
                && !EssentialsHook.isVanished(player))
        {
            quitMessage.set(QuitMessageGenerator.generate(player));
        }
        else
        {
            quitMessage.set(null);
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    private void onMove(PlayerMoveEvent event)
    {
        Player player = event.getPlayer();
        Session session = getSessionManager().getSession(player);
        
        if (session != null)
        {
            session.resetInactivityTime();
        }
        
        if (!getSessionManager().isSessionAlive(player)
                && getCore().isPlayerForcedToLogIn(player))
        {
            boolean preventMovement = false;
            
            if (getConfig("config.yml").getBoolean("forceLogin.prevent.move"))
            {
                preventMovement = true;
            }
            else if (session != null)
            {
                int moveRadius = getConfig("config.yml")
                        .getInt("forceLogin.moveRadius");
                
                if (moveRadius >= 0)
                {
                    Location spawnLocation =
                            session.getLastForceLoginLocation();
                    
                    if (spawnLocation != null)
                    {
                        spawnLocation = spawnLocation.clone();
                        spawnLocation.setY(event.getTo().getY());
                        
                        double fromDistance =
                                event.getFrom().distanceSquared(spawnLocation);
                        double toDistance =
                                event.getTo().distanceSquared(spawnLocation);
                        
                        if (toDistance > fromDistance)
                        {
                            preventMovement =
                                    toDistance > (moveRadius * moveRadius);
                        }
                    }
                }
            }
            
            if (preventMovement)
            {
                event.setTo(event.getFrom());
            }
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    private void onToggleSneak(PlayerToggleSneakEvent event)
    {
        Player player = event.getPlayer();
        Session session = getSessionManager().getSession(player);
        
        if (session != null)
        {
            session.resetInactivityTime();
        }
        
        if (event.isCancelled())
            return;
        
        if (getConfig("config.yml").getBoolean("forceLogin.prevent.toggleSneak")
                && !getSessionManager().isSessionAlive(player)
                && getCore().isPlayerForcedToLogIn(player))
        {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    private void onChat_NORMAL(AsyncPlayerChatEvent event)
    {
        if (!isCoreStarted())
            return;
        
        Player player = event.getPlayer();
        Session session = getSessionManager().getSession(player);
        
        if (session != null)
        {
            session.resetInactivityTime();
        }
        
        if (event.isCancelled())
            return;
        
        if (getConfig("config.yml").getBoolean("forceLogin.prevent.chat")
                && !getSessionManager().isSessionAlive(player)
                && getCore().isPlayerForcedToLogIn(player))
        {
            event.setCancelled(true);
            getMessageDispatcher().sendForceLoginMessage(player);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onChat_MONITOR(AsyncPlayerChatEvent event)
    {
        if (!isCoreStarted())
            return;
        
        if (!getConfig("config.yml").getBoolean("forceLogin.hideChatMessages"))
            return;
        
        Iterator<Player> recipients = event.getRecipients().iterator();
        
        while (recipients.hasNext())
        {
            Player recipient = recipients.next();
            
            if (!getSessionManager().isSessionAlive(recipient)
                    && getCore().isPlayerForcedToLogIn(recipient))
            {
                recipients.remove();
            }
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    private void onCommandPreprocess(PlayerCommandPreprocessEvent event)
    {
        if (!isCoreStarted())
            return;
        
        Player player = event.getPlayer();
        Session session = getSessionManager().getSession(player);
        
        if (session != null)
        {
            session.resetInactivityTime();
        }
        
        if (event.isCancelled())
            return;
        
        if (!getConfig("config.yml").getBoolean("forceLogin.prevent.commandPreprocess"))
            return;
        
        String message = event.getMessage();
        PluginCommand loginCommand = getPlugin().getCommand("login");
        PluginCommand registerCommand = getPlugin().getCommand("register");
        
        if (matchesCommand(loginCommand, message)
                || matchesCommand(registerCommand, message))
        {
            return;
        }
        
        List<String> allowedCommands = getConfig("config.yml")
                .getStringList("forceLogin.allowedCommands");
        
        if (matchesCommand(allowedCommands, message))
        {
            return;
        }
        
        if (!getSessionManager().isSessionAlive(player)
                && getCore().isPlayerForcedToLogIn(player))
        {
            event.setCancelled(true);
            getMessageDispatcher().sendForceLoginMessage(player);
        }
    }
    
    private boolean matchesCommand(PluginCommand command, String message)
    {
        if (command == null || message == null)
            throw new IllegalArgumentException();
        
        String commandLabel = command.getLabel();
        String lowerMessage = message.toLowerCase();
        
        if (message.equalsIgnoreCase("/" + commandLabel))
            return true;
        
        if (lowerMessage.startsWith("/" + commandLabel.toLowerCase() + " "))
            return true;
        
        return matchesCommand(command.getAliases(), message);
    }
    
    private boolean matchesCommand(Collection<String> matchers, String message)
    {
        if (matchers == null || message == null)
            throw new IllegalArgumentException();
        
        String lowerMessage = message.toLowerCase();
        
        for (String matcher : matchers)
        {
            String lowerMatcher = matcher.toLowerCase();
            
            if (message.equalsIgnoreCase("/" + matcher)
                    || lowerMessage.startsWith("/" + lowerMatcher + " "))
            {
                return true;
            }
            
            if (message.equalsIgnoreCase(matcher)
                    || lowerMessage.startsWith(lowerMatcher + " "))
            {
                return true;
            }
        }
        
        return false;
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    private void onInteract(PlayerInteractEvent event)
    {
        Player player = event.getPlayer();
        Session session = getSessionManager().getSession(player);
        
        if (session != null)
        {
            session.resetInactivityTime();
        }
        
        if (event.isCancelled())
            return;
        
        if (getConfig("config.yml").getBoolean("forceLogin.prevent.interact")
                && !getSessionManager().isSessionAlive(player)
                && getCore().isPlayerForcedToLogIn(player))
        {
            event.setCancelled(true);
            
            Block clickedBlock = event.getClickedBlock();
            
            // Check if not on a pressure plate to prevent spamming.
            if (clickedBlock == null || (clickedBlock.getType() != Material.WOOD_PLATE
                    && clickedBlock.getType() != Material.STONE_PLATE))
            {
                if (getConfig("config.yml").getBoolean("forceLogin.promptOn.interact"))
                {
                    getMessageDispatcher().sendForceLoginMessage(player);
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    private void onInteractEntity(PlayerInteractEntityEvent event)
    {
        Player player = event.getPlayer();
        Session session = getSessionManager().getSession(player);
        
        if (session != null)
        {
            session.resetInactivityTime();
        }
        
        if (event.isCancelled())
            return;
        
        if (getConfig("config.yml").getBoolean("forceLogin.prevent.interactEntity")
                && !getSessionManager().isSessionAlive(player)
                && getCore().isPlayerForcedToLogIn(player))
        {
            event.setCancelled(true);
            
            if (getConfig("config.yml").getBoolean("forceLogin.promptOn.interactEntity"))
            {
                getMessageDispatcher().sendForceLoginMessage(player);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    private void onPickupItem(PlayerPickupItemEvent event)
    {
        if (!getConfig("config.yml").getBoolean("forceLogin.prevent.pickupItem"))
            return;
        
        Player player = event.getPlayer();
        
        if (!getSessionManager().isSessionAlive(player)
                && getCore().isPlayerForcedToLogIn(player))
        {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    private void onDropItem(PlayerDropItemEvent event)
    {
        Player player = event.getPlayer();
        Session session = getSessionManager().getSession(player);
        
        if (session != null)
        {
            session.resetInactivityTime();
        }
        
        if (event.isCancelled())
            return;
        
        if (getConfig("config.yml").getBoolean("forceLogin.prevent.dropItem")
                && !getSessionManager().isSessionAlive(player)
                && getCore().isPlayerForcedToLogIn(player))
        {
            event.setCancelled(true);
            
            if (getConfig("config.yml").getBoolean("forceLogin.promptOn.dropItem"))
            {
                getMessageDispatcher().sendForceLoginMessage(player);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    private void onChangedWorld(PlayerChangedWorldEvent event)
    {
        Player player = event.getPlayer();
        
        if (!getSessionManager().isSessionAlive(player)
                && getCore().isPlayerForcedToLogIn(player))
        {
            getMessageDispatcher().sendForceLoginMessage(player);
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    private void onRespawn(PlayerRespawnEvent event)
    {
        Player player = event.getPlayer();
        String playerName = player.getName();
        
        if (getConfig("config.yml").getBoolean("waitingRoom.enabled")
                && !getSessionManager().isSessionAlive(player)
                && getCore().isPlayerForcedToLogIn(player))
        {
            if (playersDeadOnJoin.contains(player))
            {
                Location respawnLocation = event.getRespawnLocation();
                
                Account account = getAccountManager().selectAccount(
                        playerName,
                        Arrays.asList(
                                keys().username(),
                                keys().persistence()
                        )
                );
                
                if (account != null)
                {
                    getPersistenceManager().unserializeUsing(
                            account, player,
                            LocationSerializer.class
                    );
                    getPersistenceManager().serializeUsing(
                            account, player,
                            new LocationSerializer.PlayerlessLocationSerializer(
                                    respawnLocation
                            )
                    );
                }
            }
            
            event.setRespawnLocation(getCore().getWaitingRoomLocation());
        }
        
        if (playersDeadOnJoin.contains(player))
        {
            playersDeadOnJoin.remove(player);
        }
    }
    
    private final Set<Player> playersDeadOnJoin = new HashSet<>();
    private Pattern usernamePattern = null;
}
