package io.github.lucaseasedup.logit;

import static io.github.lucaseasedup.logit.message.MessageHelper.t;
import io.github.lucaseasedup.logit.account.Account;
import io.github.lucaseasedup.logit.account.AccountKeys;
import io.github.lucaseasedup.logit.account.AccountManager;
import io.github.lucaseasedup.logit.account.AccountWatcher;
import io.github.lucaseasedup.logit.backup.BackupManager;
import io.github.lucaseasedup.logit.command.AcclockCommand;
import io.github.lucaseasedup.logit.command.AccunlockCommand;
import io.github.lucaseasedup.logit.command.ChangeEmailCommand;
import io.github.lucaseasedup.logit.command.ChangePassCommand;
import io.github.lucaseasedup.logit.command.DisabledCommandExecutor;
import io.github.lucaseasedup.logit.command.LogItTabCompleter;
import io.github.lucaseasedup.logit.command.LoginCommand;
import io.github.lucaseasedup.logit.command.LoginHistoryCommand;
import io.github.lucaseasedup.logit.command.LogoutCommand;
import io.github.lucaseasedup.logit.command.ProfileCommand;
import io.github.lucaseasedup.logit.command.RecoverPassCommand;
import io.github.lucaseasedup.logit.command.RegisterCommand;
import io.github.lucaseasedup.logit.command.RememberCommand;
import io.github.lucaseasedup.logit.command.UnregisterCommand;
import io.github.lucaseasedup.logit.common.CancellableEvent;
import io.github.lucaseasedup.logit.common.Disposable;
import io.github.lucaseasedup.logit.common.FatalReportedException;
import io.github.lucaseasedup.logit.common.PlayerCollections;
import io.github.lucaseasedup.logit.common.Timer;
import io.github.lucaseasedup.logit.common.Wrapper;
import io.github.lucaseasedup.logit.config.ConfigurationManager;
import io.github.lucaseasedup.logit.config.InvalidPropertyValueException;
import io.github.lucaseasedup.logit.config.PredefinedConfiguration;
import io.github.lucaseasedup.logit.config.TimeUnit;
import io.github.lucaseasedup.logit.cooldown.CooldownManager;
import io.github.lucaseasedup.logit.craftreflect.CraftReflect;
import io.github.lucaseasedup.logit.hooks.VaultHook;
import io.github.lucaseasedup.logit.listener.BlockEventListener;
import io.github.lucaseasedup.logit.listener.EntityEventListener;
import io.github.lucaseasedup.logit.listener.InventoryEventListener;
import io.github.lucaseasedup.logit.listener.JoinMessage;
import io.github.lucaseasedup.logit.listener.PlayerEventListener;
import io.github.lucaseasedup.logit.listener.PlayerKicker;
import io.github.lucaseasedup.logit.listener.QuitMessage;
import io.github.lucaseasedup.logit.listener.ServerEventListener;
import io.github.lucaseasedup.logit.listener.SessionEventListener;
import io.github.lucaseasedup.logit.locale.EnglishLocale;
import io.github.lucaseasedup.logit.locale.GermanLocale;
import io.github.lucaseasedup.logit.locale.LocaleManager;
import io.github.lucaseasedup.logit.locale.PolishLocale;
import io.github.lucaseasedup.logit.logging.CommandSilencer;
import io.github.lucaseasedup.logit.logging.LogItCoreLogger;
import io.github.lucaseasedup.logit.logging.timing.TakeoffTiming;
import io.github.lucaseasedup.logit.logging.timing.Timing;
import io.github.lucaseasedup.logit.message.LogItMessageDispatcher;
import io.github.lucaseasedup.logit.message.MessageHelper;
import io.github.lucaseasedup.logit.persistence.AirBarSerializer;
import io.github.lucaseasedup.logit.persistence.ExperienceSerializer;
import io.github.lucaseasedup.logit.persistence.HealthBarSerializer;
import io.github.lucaseasedup.logit.persistence.HungerBarSerializer;
import io.github.lucaseasedup.logit.persistence.LocationSerializer;
import io.github.lucaseasedup.logit.persistence.PersistenceManager;
import io.github.lucaseasedup.logit.persistence.PersistenceSerializer;
import io.github.lucaseasedup.logit.profile.ProfileManager;
import io.github.lucaseasedup.logit.security.GlobalPasswordManager;
import io.github.lucaseasedup.logit.security.SecurityHelper;
import io.github.lucaseasedup.logit.session.SessionManager;
import io.github.lucaseasedup.logit.storage.CacheType;
import io.github.lucaseasedup.logit.storage.DataType;
import io.github.lucaseasedup.logit.storage.Storage;
import io.github.lucaseasedup.logit.storage.StorageFactory;
import io.github.lucaseasedup.logit.storage.StorageType;
import io.github.lucaseasedup.logit.storage.UnitKeys;
import io.github.lucaseasedup.logit.storage.WrapperStorage;
import io.github.lucaseasedup.logit.tab.TabListUpdater;
import io.github.lucaseasedup.logit.util.CollectionUtils;
import io.github.lucaseasedup.logit.util.IoUtils;
import io.github.lucaseasedup.logit.util.PlayerUtils;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.mcsg.double0negative.tabapi.TabAPI;

/**
 * The central part of LogIt.
 * 
 * <p> There is only one instance of {@code LogItCore}, obtainable using
 * {@link #getInstance()}.
 */
public final class LogItCore
{
    private LogItCore(LogItPlugin plugin)
    {
        if (plugin == null)
            throw new IllegalArgumentException();
        
        this.plugin = plugin;
    }
    
    /**
     * Starts up the LogIt core.
     *
     * @return A {@code CancellableState} indicating whether this operation
     *         has been cancelled by one of the {@code LogItCoreStartEvent}
     *         handlers.
     *
     * @throws FatalReportedException
     *        If a critical error occurred and LogIt could not start.
     *
     * @throws IllegalStateException
     *        If the LogIt core is already running.
     *
     * @see #isStarted()
     * @see #stop()
     */
    public CancelledState start() throws FatalReportedException
    {
        if (isStarted())
        {
            throw new IllegalStateException(
                    "The LogIt core has already been started."
            );
        }
        
        TakeoffTiming timing = new TakeoffTiming();
        timing.start();
        
        // =======================================
        timing.startEvent();
        
        CancellableEvent evt = new LogItCoreStartEvent(this);
        
        Bukkit.getPluginManager().callEvent(evt);
        
        if (evt.isCancelled())
            return CancelledState.CANCELLED;
        
        timing.endEvent();
        // =======================================
        
        scheduleTask(new BukkitRunnable()
        {
            @Override
            public void run()
            {
                globalClock.advance();
            }
        }, 0L, globalClock.getInterval());
        
        getDataFolder().mkdir();
        
        firstRun = !getDataFile("config.yml").exists();
        
        if (isFirstRun())
        {
            doFirstRunStuff();
        }
        
        // =======================================
        timing.startConfigurationManager();
        
        setUpConfiguration();
        
        timing.endConfigurationManager();
        // =======================================
        
        // =======================================
        timing.startLogger();
        
        setUpLogger();
        
        timing.endLogger();
        // =======================================
        timing.startMessages();
        
        try
        {
            getPlugin().loadMessages(
                    getConfig("config.yml").getString("locale")
            );
        }
        catch (IOException ex)
        {
            log(Level.WARNING, "Could not load messages.", ex);
        }
        
        timing.endMessages();
        // =======================================
        
        if (getConfig("config.yml").getBoolean("passwordRecovery.enabled"))
        {
            File bodyFile = getDataFile(
                    getConfig("config.yml").getString("passwordRecovery.bodyTemplate")
            );
            
            if (!bodyFile.isFile())
            {
                log(Level.SEVERE, "File " + bodyFile + " not found");
                
                FatalReportedException.throwNew();
            }
        }
        
        if (getConfig("secret.yml").getBoolean("debug.enableSelfTests"))
        {
            log(Level.WARNING, "Self-tests for LogIt have been enabled." +
                    " Using them will DESTROY ALL YOUR DATA!");
            
            tellConsole("");
            tellConsole(ChatColor.RED + "()()()()()()()()()()()()()()()()()()()()");
            tellConsole(ChatColor.RED + "========================================");
            tellConsole("");
            tellConsole(ChatColor.RED + "          !!! ATTENTION !!!");
            tellConsole("");
            tellConsole(ChatColor.RED + " Self-tests for LogIt have been enabled.");
            tellConsole(ChatColor.RED + " Using them will DESTROY ALL YOUR DATA!");
            tellConsole("");
            tellConsole(ChatColor.RED + "========================================");
            tellConsole(ChatColor.RED + "()()()()()()()()()()()()()()()()()()()()");
            tellConsole("");
        }
        
        // =======================================
        timing.startCraftReflect();
        
        setUpCraftReflect();
        
        timing.endCraftReflect();
        // =======================================
        
        setUpLocaleManager();
        
        // =======================================
        timing.startAccountManager();
        
        setUpAccountManager();
        
        timing.endAccountManager();
        // =======================================
        
        // =======================================
        timing.startPersistenceManager();
        
        setUpPersistenceManager();
        
        timing.endPersistenceManager();
        // =======================================

        disposables.add(securityHelper = new SecurityHelper());
        disposables.add(backupManager = new BackupManager(getAccountManager()));
        disposables.add(sessionManager = new SessionManager());
        disposables.add(messageDispatcher = new LogItMessageDispatcher());
        disposables.add(tabCompleter = new LogItTabCompleter());
        
        if (getConfig("config.yml").getBoolean("profiles.enabled"))
        {
            setUpProfileManager();
        }
        
        disposables.add(globalPasswordManager = new GlobalPasswordManager());
        disposables.add(cooldownManager = new CooldownManager());
        disposables.add(accountWatcher = new AccountWatcher());
        tabApiWrapper = new Wrapper<>();
        
        if (getConfig("config.yml").getBoolean("forceLogin.hideFromTabList"))
        {
            disposables.add(tabListUpdater = new TabListUpdater(
                    tabApiWrapper, craftReflect
            ));
            
            new BukkitRunnable()
            {
                @Override
                public void run()
                {
                    if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null)
                    {
                        tabApiWrapper.set(new TabAPI());
                        tabApiWrapper.get().onEnable();
                    }
                }
            }.runTaskLater(getPlugin(), 1L);
        }
        
        startTasks();
        enableCommands();
        registerEventListeners();
        
        timing.end();
        
        if (getConfig("secret.yml").getBoolean("timings.enabled"))
        {
            clearTimings();
            saveTiming(timing);
        }
        
        started = true;
        
        log(Level.FINE, t("startPlugin.success"));
        
        if (isFirstRun())
        {
            tellConsole(t("firstRun1"));
            tellConsole(t("firstRun2"));
            tellConsole(t("firstRun3"));
        }
        
        doPostStartDuties();
        
        return CancelledState.NOT_CANCELLED;
    }
    
    private void doFirstRunStuff()
    {
        getDataFile("backup").mkdir();
        getDataFile("lib").mkdir();
        getDataFile("mail").mkdir();

        extractMailTemplate("password-recovery.html");
    }
    
    private void setUpConfiguration() throws FatalReportedException
    {
        String configHeader =
                "# # # # # # # # # # # # # # #\n"
              + " LogIt Configuration File   #\n"
              + "# # # # # # # # # # # # # # #\n";
        
        String statsHeader =
                "# # # # # # # # # # # # # # #\n"
              + "  LogIt Statistics File     #\n"
              + "# # # # # # # # # # # # # # #\n";
        
        String secretHeader =
                "# # # # # # # # # # # # # # # # # # # # # # # # # # # #\n"
              + "             LogIt Secret Settings File               #\n"
              + "                                                      #\n"
              + " Do not touch unless you are 100% what you're doing!  #\n"
              + "# # # # # # # # # # # # # # # # # # # # # # # # # # # #\n";
        
        File oldConfigDefFile = getDataFile("config-def.b64");
        
        if (oldConfigDefFile.isFile())
        {
            File newConfigDefFile = getDataFile(".doNotTouch/config-def.b64");
            
            try
            {
                IoUtils.copyFile(oldConfigDefFile, newConfigDefFile);
                
                oldConfigDefFile.delete();
            }
            catch (IOException ex)
            {
                log(Level.WARNING, ex);
            }
        }
        
        disposables.add(configurationManager = new ConfigurationManager());
        configurationManager.registerConfiguration("config.yml",
                ".doNotTouch/config-def.b64", "config-def.ini", configHeader);
        configurationManager.registerConfiguration("stats.yml",
                ".doNotTouch/stats-def.b64", "stats-def.ini", statsHeader);
        configurationManager.registerConfiguration("secret.yml",
                ".doNotTouch/secret-def.b64", "secret-def.ini", secretHeader);
        
        try
        {
            configurationManager.loadAll();
        }
        catch (IOException ex)
        {
            log(Level.SEVERE, "Could not load a configuration file.", ex);
            
            FatalReportedException.throwNew(ex);
        }
        catch (InvalidPropertyValueException ex)
        {
            log(Level.SEVERE, ex.getMessage());
            
            FatalReportedException.throwNew(ex);
        }
    }
    
    private void setUpLogger()
    {
        disposables.add(logger = new LogItCoreLogger(this));
        logger.open();

        disposables.add(commandSilencer = new CommandSilencer(Arrays.asList(
                getPlugin().getCommand("login"),
                getPlugin().getCommand("logout"),
                getPlugin().getCommand("register"),
                getPlugin().getCommand("unregister"),
                getPlugin().getCommand("changepass"),
                getPlugin().getCommand("changeemail"),
                getPlugin().getCommand("recoverpass"),
                getPlugin().getCommand("loginhistory")
        )));
        commandSilencer.registerFilters();
    }
    
    private void extractMailTemplate(String filename)
    {
        if (filename == null)
            throw new IllegalArgumentException();
        
        File templateFile = getDataFile("mail", filename);
        
        if (!templateFile.exists())
        {
            try
            {
                IoUtils.extractResource(filename, templateFile);
            }
            catch (IOException ex)
            {
                log(Level.WARNING,
                        "Could not extract mail template: " + filename, ex);
            }
        }
    }
    
    private void setUpCraftReflect()
    {
        try
        {
            String version = LogItPlugin.getCraftBukkitVersion();
            String craftClassName = LogItPlugin.PACKAGE + ".craftreflect."
                        + version + ".CraftReflect";
            Class<?> craftClass = Class.forName(craftClassName);
            
            craftReflect = (CraftReflect)
                    craftClass.getConstructor().newInstance();
        }
        catch (ClassNotFoundException ex)
        {
            log(Level.WARNING, "LogIt does not support this version of Bukkit."
                    + " Some features may not work.");
        }
        catch (ReflectiveOperationException ex)
        {
            log(Level.WARNING, "Could not set up CraftBukkit reflection."
                    + " Some features may not work.", ex);
        }
    }
    
    private void setUpLocaleManager()
    {
        disposables.add(localeManager = new LocaleManager());
        localeManager.registerLocale(EnglishLocale.getInstance());
        localeManager.registerLocale(PolishLocale.getInstance());
        localeManager.registerLocale(GermanLocale.getInstance());
        localeManager.setFallbackLocale(EnglishLocale.class);
        localeManager.switchActiveLocale(getConfig("config.yml").getString("locale"));
    }
    
    private void setUpAccountManager() throws FatalReportedException
    {
        StorageType leadingStorageType = StorageType.decode(
                getConfig("config.yml").getString("storage.accounts.leading.storageType")
        );
        StorageType mirrorStorageType = StorageType.decode(
                getConfig("config.yml").getString("storage.accounts.mirror.storageType")
        );
        
        String accountsUnit = getConfig("config.yml")
                .getString("storage.accounts.leading.unit");
        AccountKeys accountKeys = new AccountKeys(
            getConfig("config.yml").getString("storage.accounts.keys.username"),
            getConfig("config.yml").getString("storage.accounts.keys.uuid"),
            getConfig("config.yml").getString("storage.accounts.keys.salt"),
            getConfig("config.yml").getString("storage.accounts.keys.password"),
            getConfig("config.yml").getString("storage.accounts.keys.hashing_algorithm"),
            getConfig("config.yml").getString("storage.accounts.keys.ip"),
            getConfig("config.yml").getString("storage.accounts.keys.login_session"),
            getConfig("config.yml").getString("storage.accounts.keys.email"),
            getConfig("config.yml").getString("storage.accounts.keys.last_active_date"),
            getConfig("config.yml").getString("storage.accounts.keys.reg_date"),
            getConfig("config.yml").getString("storage.accounts.keys.is_locked"),
            getConfig("config.yml").getString("storage.accounts.keys.login_history"),
            getConfig("config.yml").getString("storage.accounts.keys.display_name"),
            getConfig("config.yml").getString("storage.accounts.keys.persistence")
        );
        
        @SuppressWarnings("resource")
        Storage leadingAccountStorage =
                new StorageFactory(getConfig("config.yml"), "storage.accounts.leading")
                        .produceStorage(leadingStorageType);
        
        @SuppressWarnings("resource")
        Storage mirrorAccountStorage =
                new StorageFactory(getConfig("config.yml"), "storage.accounts.mirror")
                        .produceStorage(mirrorStorageType);
        
        CacheType accountCacheType = CacheType.decode(
                getConfig("config.yml").getString("storage.accounts.leading.cache")
        );
        
        String leadingUnit = getConfig("config.yml")
                .getString("storage.accounts.leading.unit");
        String mirrorUnit = getConfig("config.yml")
                .getString("storage.accounts.mirror.unit");
        
        @SuppressWarnings("resource")
        WrapperStorage accountStorage = new WrapperStorage.Builder()
                .leading(leadingAccountStorage)
                .cacheType(accountCacheType)
                .build();
        Map<String, String> unitMappings = new HashMap<>();
        unitMappings.put(leadingUnit, mirrorUnit);
        accountStorage.mirrorStorage(mirrorAccountStorage, unitMappings);
        
        try
        {
            accountStorage.connect();
        }
        catch (IOException ex)
        {
            log(Level.SEVERE, "Could not establish database connection", ex);
            
            FatalReportedException.throwNew(ex);
        }
        
        try
        {
            accountStorage.createUnit(accountsUnit,
                    accountKeys, accountKeys.username());
        }
        catch (IOException ex)
        {
            log(Level.SEVERE, "Could not create accounts unit", ex);
            
            FatalReportedException.throwNew(ex);
        }
        
        try
        {
            accountStorage.setAutobatchEnabled(true);
            
            UnitKeys existingKeys = accountStorage.getKeys(accountsUnit);
            
            for (Map.Entry<String, DataType> e : accountKeys.entrySet())
            {
                if (!existingKeys.containsKey(e.getKey()))
                {
                    accountStorage.addKey(accountsUnit, e.getKey(), e.getValue());
                }
            }
            
            accountStorage.executeBatch();
            accountStorage.clearBatch();
            accountStorage.setAutobatchEnabled(false);
        }
        catch (IOException ex)
        {
            log(Level.SEVERE, "Could not update accounts table columns", ex);
            
            FatalReportedException.throwNew(ex);
        }
        
        try
        {
            accountStorage.preload(leadingUnit);
        }
        catch (IOException ex)
        {
            log(Level.SEVERE, "Could not preload accounts", ex);
        }
        
        try
        {
            disposables.add(accountManager = new AccountManager(
                    accountStorage, accountsUnit, accountKeys
            ));
        }
        catch (IOException ex)
        {
            log(Level.SEVERE, "Could not construct AccountManager", ex);
        }
    }
    
    private void setUpPersistenceManager() throws FatalReportedException
    {
        disposables.add(persistenceManager = new PersistenceManager());
        
        setSerializerEnabled(LocationSerializer.class,
                getConfig("config.yml").getBoolean("waitingRoom.enabled"));
        setSerializerEnabled(AirBarSerializer.class,
                getConfig("config.yml").getBoolean("forceLogin.obfuscate.air"));
        setSerializerEnabled(HealthBarSerializer.class,
                getConfig("config.yml").getBoolean("forceLogin.obfuscate.health"));
        setSerializerEnabled(ExperienceSerializer.class,
                getConfig("config.yml").getBoolean("forceLogin.obfuscate.experience"));
        setSerializerEnabled(HungerBarSerializer.class,
                getConfig("config.yml").getBoolean("forceLogin.obfuscate.hunger"));
    }
    
    private void setSerializerEnabled(Class<? extends PersistenceSerializer> clazz,
                                      boolean status)
            throws FatalReportedException
    {
        if (status)
        {
            try
            {
                getPersistenceManager().registerSerializer(clazz);
            }
            catch (ReflectiveOperationException ex)
            {
                log(Level.SEVERE, "Could not register persistence serializer: "
                            + clazz.getSimpleName());
                
                FatalReportedException.throwNew(ex);
            }
        }
        else
        {
            AccountKeys keys = getAccountManager().getKeys();
            
            for (Player player : PlayerUtils.getOnlinePlayers())
            {
                Account account = getAccountManager().selectAccount(player.getName(),
                        Arrays.asList(
                                keys.username(),
                                keys.persistence()
                        )
                );
                
                if (account != null)
                {
                    getPersistenceManager().unserializeUsing(
                            account, player, clazz
                    );
                }
            }
        }
    }
    
    private void setUpProfileManager()
    {
        File profilesPath = getDataFile(
                getConfig("config.yml").getString("profiles.path")
        );
        
        if (!profilesPath.exists())
        {
            profilesPath.getParentFile().mkdirs();
            profilesPath.mkdir();
        }
        
        disposables.add(profileManager = new ProfileManager(
                profilesPath,
                getConfig("config.yml").getValues("profiles.fields")
        ));
    }
    
    private void startTasks()
    {
        long bufferFlushInterval = getConfig("secret.yml")
                .getTime("bufferFlushInterval", TimeUnit.TICKS);
        
        scheduleTask(getAccountManager(), 0L, bufferFlushInterval);
        scheduleTask(getBackupManager(), 0L, BackupManager.TASK_PERIOD);
        scheduleTask(getSessionManager(), 0L, SessionManager.TASK_PERIOD);
        scheduleTask(getGlobalPasswordManager(), 0L, GlobalPasswordManager.TASK_PERIOD);
        scheduleTask(getAccountWatcher(), 0L, AccountWatcher.TASK_PERIOD);
        
        if (getTabListUpdater() != null)
        {
            scheduleTask(getTabListUpdater(), 20L, TabListUpdater.TASK_PERIOD);
        }
    }
    
    private void scheduleTask(Runnable runnable, long delay, long period)
    {
        if (runnable == null || delay < 0 || period <= 0)
            throw new IllegalArgumentException();
        
        tasks.add(Bukkit.getScheduler().runTaskTimer(
                getPlugin(), runnable, delay, period
        ));
    }
    
    private void enableCommands()
    {
        enableCommand("login", new LoginCommand());
        enableCommand("logout", new LogoutCommand());
        enableCommand("remember", new RememberCommand(),
                getConfig("config.yml").getBoolean("loginSessions.enabled"));
        enableCommand("register", new RegisterCommand());
        enableCommand("unregister", new UnregisterCommand());
        enableCommand("changepass", new ChangePassCommand(),
                !getConfig("secret.yml").getBoolean("passwords.disable"));
        enableCommand("changeemail", new ChangeEmailCommand());
        enableCommand("recoverpass", new RecoverPassCommand(),
                getConfig("config.yml").getBoolean("passwordRecovery.enabled"));
        enableCommand("profile", new ProfileCommand(),
                getConfig("config.yml").getBoolean("profiles.enabled"));
        enableCommand("acclock", new AcclockCommand());
        enableCommand("accunlock", new AccunlockCommand());
        enableCommand("loginhistory", new LoginHistoryCommand(),
                getConfig("config.yml").getBoolean("loginHistory.enabled"));
    }
    
    private void enableCommand(
            String command, CommandExecutor executor, boolean enabled
    )
    {
        if (command == null || executor == null)
            throw new IllegalArgumentException();
        
        if (enabled)
        {
            getPlugin().getCommand(command).setExecutor(executor);
        }
        else
        {
            disableCommand(command);
        }
    }
    
    private void enableCommand(String command, CommandExecutor executor)
    {
        enableCommand(command, executor, true);
    }
    
    private void registerEventListeners()
    {
        PlayerCollections.registerListener(getPlugin());
        
        registerEventListener(getMessageDispatcher());
        registerEventListener(getCooldownManager());
        
        if (getTabListUpdater() != null)
        {
            registerEventListener(getTabListUpdater());
        }
        
        registerEventListener(new ServerEventListener());
        registerEventListener(new BlockEventListener());
        registerEventListener(new EntityEventListener());
        registerEventListener(new PlayerEventListener());
        registerEventListener(new InventoryEventListener());
        registerEventListener(new SessionEventListener());
    }
    
    private <T extends Listener> void registerEventListener(T listener)
    {
        if (listener == null)
            throw new IllegalArgumentException();
        
        Bukkit.getPluginManager().registerEvents(listener, getPlugin());
        
        eventListeners.put(listener.getClass(), listener);
    }
    
    private void doPostStartDuties()
    {
        if (getConfig("config.yml").getBoolean("backup.forceAtStart"))
        {
            backupManager.createBackup();
        }
        
        File sessionsFile = getDataFile(
                getConfig("config.yml").getString("storage.sessions.filename")
        );
        
        if (getSessionManager() != null && sessionsFile.isFile())
        {
            try
            {
                getSessionManager().importSessions(sessionsFile);
            }
            catch (IOException ex)
            {
                log(Level.WARNING, "Could not import sessions.", ex);
            }
            
            sessionsFile.delete();
        }
        
        PlayerEventListener playerEventListener =
                getEventListener(PlayerEventListener.class);
        PlayerKicker playerKicker = new PlayerKicker()
        {
            @Override
            public void kick(Player player, String message)
            {
                player.kickPlayer(message);
            }
        };
        
        for (final Player player : PlayerUtils.getOnlinePlayers())
        {
            playerEventListener.onLogin(player,
                    player.getAddress().getAddress(), playerKicker);
            playerEventListener.onJoin(player, new JoinMessage()
            {
                @Override
                public void set(String joinMessage)
                {
                    MessageHelper.broadcastMsgExcept(joinMessage,
                            Arrays.asList(player.getName()));
                }
            });
        }
    }
    
    /**
     * Stops the LogIt core.
     *
     * @return A {@code CancellableState} indicating whether this operation
     *         has been cancelled by one of the {@code LogItCoreStopEvent}
     *         handlers.
     *
     * @throws IllegalStateException
     *        If the LogIt core is not running.
     * 
     * @see #isStarted()
     * @see #start()
     */
    public CancelledState stop()
    {
        if (!isStarted())
            throw new IllegalStateException("The LogIt core is not started.");
        
        CancellableEvent evt = new LogItCoreStopEvent(this);
        
        Bukkit.getPluginManager().callEvent(evt);
        
        if (evt.isCancelled())
            return CancelledState.CANCELLED;
        
        File sessionsFile = getDataFile(
                getConfig("config.yml").getString("storage.sessions.filename")
        );
        
        if (getSessionManager() != null)
        {
            try
            {
                getSessionManager().exportSessions(sessionsFile);
            }
            catch (IOException ex)
            {
                log(Level.WARNING, "Could not export sessions.", ex);
            }
        }
        
        PlayerEventListener playerEventListener =
                getEventListener(PlayerEventListener.class);
        
        if (playerEventListener != null)
        {
            for (final Player player : PlayerUtils.getOnlinePlayers())
            {
                playerEventListener.onQuit(player, new QuitMessage()
                {
                    @Override
                    public void set(String quitMessage)
                    {
                        MessageHelper.broadcastMsgExcept(quitMessage,
                                Arrays.asList(player.getName()));
                    }
                });
            }
        }
        
        disableCommands();
        
        if (getPersistenceManager() != null)
        {
            getPersistenceManager()
                    .unregisterSerializer(LocationSerializer.class);
            getPersistenceManager()
                    .unregisterSerializer(AirBarSerializer.class);
            getPersistenceManager()
                    .unregisterSerializer(HealthBarSerializer.class);
            getPersistenceManager()
                    .unregisterSerializer(ExperienceSerializer.class);
            getPersistenceManager()
                    .unregisterSerializer(HungerBarSerializer.class);
        }
        
        if (getAccountManager() != null)
        {
            try
            {
                getAccountManager().getStorage().close();
            }
            catch (IOException ex)
            {
                log(Level.WARNING, "Could not close database connection.", ex);
            }
        }
        
        if (commandSilencer != null)
        {
            commandSilencer.unregisterFilters();
        }
        
        if (tabApiWrapper != null && tabApiWrapper.get() != null)
        {
            tabApiWrapper.get().onDisable();
            tabApiWrapper.set(null);
        }
        
        Bukkit.getScheduler().cancelTasks(getPlugin());
        tasks.clear();
        
        // Unregister all event listeners.
        HandlerList.unregisterAll(getPlugin());
        eventListeners.clear();
        
        started = false;
        
        dispose();
        
        log(Level.FINE, t("stopPlugin.success"));
        
        if (logger != null)
        {
            logger.close();
            logger = null;
        }
        
        return CancelledState.NOT_CANCELLED;
    }
    
    private void disableCommands()
    {
        disableCommand("login");
        disableCommand("logout");
        disableCommand("remember");
        disableCommand("register");
        disableCommand("unregister");
        disableCommand("changepass");
        disableCommand("changeemail");
        disableCommand("recoverpass");
        disableCommand("profile");
        disableCommand("acclock");
        disableCommand("accunlock");
        disableCommand("loginhistory");
    }
    
    private void disableCommand(String command)
    {
        if (command == null)
            throw new IllegalArgumentException();
        
        getPlugin().getCommand(command).setExecutor(
                new DisabledCommandExecutor()
        );
    }
    
    /**
     * Disposes the LogIt core.
     * 
     * @throws IllegalStateException if the LogIt core is running.
     */
    private void dispose()
    {
        if (isStarted())
        {
            throw new IllegalStateException(
                    "Cannot dispose the LogIt core while it's running"
            );
        }
        
        Disposable disposable;
        
        while ((disposable = disposables.poll()) != null)
        {
            disposable.dispose();
        }
        
        configurationManager = null;
        commandSilencer = null;
        localeManager = null;
        accountManager = null;
        persistenceManager = null;
        securityHelper = null;
        backupManager = null;
        sessionManager = null;
        messageDispatcher = null;
        tabCompleter = null;
        profileManager = null;
        globalPasswordManager = null;
        cooldownManager = null;
        accountWatcher = null;
        tabApiWrapper = null;
        tabListUpdater = null;
    }
    
    /**
     * Restarts the LogIt core.
     *
     * @throws FatalReportedException
     *        If the LogIt core could not be started again.
     *
     * @throws IllegalStateException
     *        If the LogIt core is not running.
     *
     * @see #isStarted()
     * @see #start()
     */
    public void restart() throws FatalReportedException
    {
        if (!isStarted())
            throw new IllegalStateException("The LogIt core is not started.");
        
        CancelledState stop = stop();
        CancelledState start = start();
        
        if (!stop.isCancelled() && !start.isCancelled())
        {
            log(Level.INFO, t("reloadPlugin.success"));
        }
    }
    
    /**
     * Checks if a player is forced to log in.
     * 
     * <p> Returns {@code true} if the <i>forceLogin.global</i> config setting
     * is set to <i>true</i>, or the player is in a world with forced login.
     * 
     * <p> If the player name is contained in the
     * <i>forceLogin.exemptPlayers</i> config property, it always returns
     * {@code false} regardless of the above conditions.
     * 
     * <p> Note that this method does not check if the player is logged in.
     * For that purpose, use {@link SessionManager#isSessionAlive(Player)}
     * or {@link SessionManager#isSessionAlive(String)}.
     * 
     * @param player
     *       The player whom this check will be ran on.
     * 
     * @return {@code true} if the player is forced to log in;
     *         {@code false} otherwise.
     */
    public boolean isPlayerForcedToLogIn(Player player)
    {
        String playerWorldName = player.getWorld().getName();
        
        boolean forcedLoginGlobal = getConfig("config.yml")
                .getBoolean("forceLogin.global");
        List<String> exemptedWorlds = getConfig("config.yml")
                .getStringList("forceLogin.inWorlds");
        List<String> exemptedPlayers = getConfig("config.yml")
                .getStringList("forceLogin.exemptPlayers");
        
        return (forcedLoginGlobal || exemptedWorlds.contains(playerWorldName))
                && !CollectionUtils.containsIgnoreCase(
                        player.getName(), exemptedPlayers
                );
    }
    
    /**
     * Updates permission groups for a player only if LogIt is linked to Vault.
     *
     * <p> If Vault is not enabled, no action will be taken.
     *
     * <p> Permission groups currently supported: <ul>
     *  <li>Registered</li>
     *  <li>Not registered</li>
     *  <li>Logged in</li>
     *  <li>Logged out</li>
     * </ul>
     *
     * <p> Exact group names will be read from the configuration file.
     *
     * @param player
     *       The player whose permission groups should be updated.
     *
     * @throws IllegalArgumentException
     *        If {@code player} is {@code null}.
     */
    public void updatePlayerGroup(Player player)
    {
        if (player == null)
            throw new IllegalArgumentException();
        
        if (!VaultHook.isVaultEnabled())
            return;
        
        if (getAccountManager().isRegistered(player.getName()))
        {
            VaultHook.playerRemoveGroup(player,
                    getConfig("config.yml").getString("groups.unregistered"));
            VaultHook.playerAddGroup(player,
                    getConfig("config.yml").getString("groups.registered"));
        }
        else
        {
            VaultHook.playerRemoveGroup(player,
                    getConfig("config.yml").getString("groups.registered"));
            VaultHook.playerAddGroup(player,
                    getConfig("config.yml").getString("groups.unregistered"));
        }
        
        if (getSessionManager().isSessionAlive(player))
        {
            VaultHook.playerRemoveGroup(player,
                    getConfig("config.yml").getString("groups.loggedOut"));
            VaultHook.playerAddGroup(player,
                    getConfig("config.yml").getString("groups.loggedIn"));
        }
        else
        {
            VaultHook.playerRemoveGroup(player,
                    getConfig("config.yml").getString("groups.loggedIn"));
            VaultHook.playerAddGroup(player,
                    getConfig("config.yml").getString("groups.loggedOut"));
        }
    }
    
    public void clearTimings()
    {
        getTimingsFile().delete();
    }
    
    public void saveTiming(Timing timing)
    {
        if (timing == null)
            throw new IllegalArgumentException();
        
        File timingsFile = getTimingsFile();
        
        try
        {
            timing.saveTiming(timingsFile);
        }
        catch (IOException ex)
        {
            log(Level.WARNING, "Could not save timings", ex);
        }
    }
    
    private File getTimingsFile()
    {
        return getDataFile(
                getConfig("secret.yml").getString("timings.filename")
        );
    }
    
    public void log(Level level, String msg)
    {
        if (level == null || msg == null)
            throw new IllegalArgumentException();
        
        msg = ChatColor.stripColor(msg);
        
        if (getLogger() == null)
        {
            getPlugin().getLogger().log(level, msg);
        }
        else
        {
            getLogger().log(level, msg);
        }
    }
    
    public void log(Level level, String msg, Throwable throwable)
    {
        if (level == null || msg == null || throwable == null)
            throw new IllegalArgumentException();
        
        msg = ChatColor.stripColor(msg);
        
        if (getLogger() == null)
        {
            getPlugin().getLogger().log(level, msg, throwable);
        }
        else
        {
            getLogger().log(level, msg, throwable);
        }
    }
    
    public void log(Level level, Throwable throwable)
    {
        if (level == null || throwable == null)
            throw new IllegalArgumentException();
        
        if (getLogger() == null)
        {
            getPlugin().getLogger().log(level, null, throwable);
        }
        else
        {
            getLogger().log(level, throwable);
        }
    }
    
    public void tellConsole(String msg)
    {
        if (msg == null)
            throw new IllegalArgumentException();
        
        Bukkit.getConsoleSender().sendMessage(msg);
    }
    
    /**
     * Returns the {@code LogItPlugin} object.
     * 
     * <p> Most of times, all the work will be done with the LogIt core,
     * but the {@code LogItPlugin} may come useful if you want to,
     * for example, reload the message files.
     * 
     * @return The {@code LogItPlugin} object.
     */
    public LogItPlugin getPlugin()
    {
        return plugin;
    }
    
    /**
     * Returns the LogIt data folder as a {@code File} object
     * (<i>/plugins/LogIt/</i>).
     * 
     * @return The data folder.
     */
    public File getDataFolder()
    {
        return getPlugin().getDataFolder();
    }
    
    /**
     * Returns a file, as a {@code File} object,
     * relative to the LogIt data folder (<i>/plugins/LogIt/</i>).
     * 
     * @param path
     *       The relative path.
     * 
     * @return The data file.
     */
    public File getDataFile(String path)
    {
        if (path == null)
            throw new IllegalArgumentException();
        
        return new File(getDataFolder(), path);
    }
    
    public File getDataFile(String parent, String path)
    {
        if (parent == null || path == null)
            throw new IllegalArgumentException();
        
        return new File(getDataFolder(), parent + File.separator + path);
    }
    
    /**
     * Checks if this is the first time LogIt is running on this server.
     * 
     * @return {@code true} if LogIt is running for the first time;
     *         {@code false} otherwise.
     */
    public boolean isFirstRun()
    {
        return firstRun;
    }
    
    /**
     * Checks if the LogIt core has been successfully started and is running.
     * 
     * @return {@code true} if the LogIt core is started;
     *         {@code false} otherwise.
     */
    public boolean isStarted()
    {
        return started;
    }
    
    public Timer getGlobalClock()
    {
        return globalClock;
    }
    
    public ConfigurationManager getConfigurationManager()
    {
        return configurationManager;
    }
    
    public PredefinedConfiguration getConfig(String filename)
    {
        if (filename == null)
            throw new IllegalArgumentException();
        
        if (getConfigurationManager() == null)
            return null;
        
        return getConfigurationManager().getConfiguration(filename);
    }
    
    public Location getWaitingRoomLocation()
    {
        return getConfig("config.yml")
                .getLocation("waitingRoom.location").toBukkitLocation();
    }
    
    private LogItCoreLogger getLogger()
    {
        return logger;
    }
    
    public LocaleManager getLocaleManager()
    {
        return localeManager;
    }
    
    public AccountManager getAccountManager()
    {
        return accountManager;
    }
    
    public PersistenceManager getPersistenceManager()
    {
        return persistenceManager;
    }
    
    public SecurityHelper getSecurityHelper()
    {
        return securityHelper;
    }
    
    public BackupManager getBackupManager()
    {
        return backupManager;
    }
    
    public SessionManager getSessionManager()
    {
        return sessionManager;
    }
    
    public LogItMessageDispatcher getMessageDispatcher()
    {
        return messageDispatcher;
    }
    
    public LogItTabCompleter getTabCompleter()
    {
        return tabCompleter;
    }
    
    public ProfileManager getProfileManager()
    {
        return profileManager;
    }
    
    public GlobalPasswordManager getGlobalPasswordManager()
    {
        return globalPasswordManager;
    }
    
    public CooldownManager getCooldownManager()
    {
        return cooldownManager;
    }
    
    private AccountWatcher getAccountWatcher()
    {
        return accountWatcher;
    }
    
    public TabListUpdater getTabListUpdater()
    {
        return tabListUpdater;
    }
    
    @SuppressWarnings("unchecked")
    public <T extends Listener> T getEventListener(Class<T> listenerClass)
    {
        if (listenerClass == null)
            throw new IllegalArgumentException();
        
        return (T) eventListeners.get(listenerClass);
    }
    
    /**
     * The preferred way to obtain the instance of {@code LogItCore}.
     * 
     * @return The instance of {@code LogItCore}.
     */
    public static LogItCore getInstance()
    {
        if (instance == null)
        {
            instance = new LogItCore(LogItPlugin.getInstance());
        }
        
        return instance;
    }
    
    private static volatile LogItCore instance = null;
    
    private final LogItPlugin plugin;
    private boolean firstRun;
    private boolean started = false;
    private final Timer globalClock = new Timer(1L);
    
    private ConfigurationManager configurationManager;
    private LogItCoreLogger logger;
    private CommandSilencer commandSilencer;
    private CraftReflect craftReflect;
    private LocaleManager localeManager;
    private AccountManager accountManager;
    private PersistenceManager persistenceManager;
    private SecurityHelper securityHelper;
    private BackupManager backupManager;
    private SessionManager sessionManager;
    private LogItMessageDispatcher messageDispatcher;
    private LogItTabCompleter tabCompleter;
    private ProfileManager profileManager;
    private GlobalPasswordManager globalPasswordManager;
    private CooldownManager cooldownManager;
    private AccountWatcher accountWatcher;
    private Wrapper<TabAPI> tabApiWrapper;
    private TabListUpdater tabListUpdater;
    
    private final Queue<Disposable> disposables = new LinkedList<>();
    private final Set<BukkitTask> tasks = new LinkedHashSet<>();
    private final Map<Class<? extends Listener>, Listener> eventListeners =
            new HashMap<>();
    
    {
        globalClock.start();
    }
}
