package io.github.lucaseasedup.logit;

import io.github.lucaseasedup.logit.command.DisabledCommandExecutor;
import io.github.lucaseasedup.logit.command.LogItCommand;
import io.github.lucaseasedup.logit.command.NopCommandExecutor;
import io.github.lucaseasedup.logit.common.FatalReportedException;
import io.github.lucaseasedup.logit.config.LocationSerializable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.PropertyResourceBundle;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.java.JavaPlugin;
//import org.mcstats.MetricsLite;

public final class LogItPlugin extends JavaPlugin
{
    /**
     * Internal method. Do not call directly.
     */
    @Override
    public void onEnable()
    {
//        try
//        {
//            MetricsLite metrics = new MetricsLite(this);
//            metrics.start();
//        }
//        catch (IOException ex)
//        {
//        }
        
        try
        {
            // Load default messages.
            loadMessages(getConfig().getString("locale", "en"));
        }
        catch (IOException ex)
        {
            // If messages could not be loaded, just log the failure.
            // They're not necessary for LogIt to work.
            getLogger().log(Level.WARNING, "Could not load messages.", ex);
        }
        
        try
        {
            loadLibraries();
        }
        catch (FatalReportedException ex)
        {
            disable();
        }
        
        getCommand("logit")
                .setExecutor(new LogItCommand());
        getCommand("$logit-nop-command")
                .setExecutor(new NopCommandExecutor());
        
        core = LogItCore.getInstance();
        
        try
        {
            core.start();
        }
        catch (FatalReportedException ex)
        {
            if (getConfig().getBoolean("terminateUnsafeServer", true))
            {
                Bukkit.getServer().shutdown();
            }
            else
            {
                disable();
            }
        }
    }
    
    /**
     * Internal method. Do not call directly.
     */
    @Override
    public void onDisable()
    {
        if (core != null)
        {
            if (core.isStarted())
            {
                core.stop();
            }
            
            core = null;
        }
        
        getCommand("logit")
                .setExecutor(new DisabledCommandExecutor());
        getCommand("$logit-nop-command")
                .setExecutor(new DisabledCommandExecutor());
        
        packageLocalMessages = null;
        userGlobalMessages = null;
        userLocalMessages = null;
    }
    
    private void enable()
    {
        getServer().getPluginManager().enablePlugin(this);
    }
    
    private void disable()
    {
        Bukkit.getConsoleSender().sendMessage(getMessage("pluginStopping"));
        
        getServer().getPluginManager().disablePlugin(this);
    }
    
    /**
     * Loads message files.
     *
     * <p> This method will also try to load user
     * global/local message files from the <i>lang</i> directory if present,
     * that will be transparently merged with built-in message files.
     *
     * @param suffix the locale suffix.
     *
     * @throws IOException if there was an error while reading.
     */
    public void loadMessages(String suffix) throws IOException
    {
        if (suffix == null)
            throw new IllegalArgumentException();
        
        loadPackageLocalMessages("messages_" + suffix + ".properties");
        loadUserGlobalMessages("lang/messages.properties");
        loadUserLocalMessages("lang/messages_" + suffix + ".properties");
    }
    
    private void loadPackageLocalMessages(String entryName) throws IOException
    {
        packageLocalMessages = null;

        try (JarFile jarFile = new JarFile(getFile()))
        {
            JarEntry jarEntry = jarFile.getJarEntry(entryName);

            if (jarEntry != null)
            {
                InputStream inputStream = jarFile.getInputStream(jarEntry);

                try (Reader reader = new InputStreamReader(inputStream, "UTF-8"))
                {
                    packageLocalMessages = new PropertyResourceBundle(reader);
                }
            }
        }
    }
    
    private void loadUserGlobalMessages(String path) throws IOException
    {
        if (path == null)
            throw new IllegalArgumentException();

        userGlobalMessages = null;

        File file = new File(getDataFolder(), path);

        if (!file.isFile())
            return;

        try (InputStream is = new FileInputStream(file))
        {
            userGlobalMessages = new PropertyResourceBundle(is);
        }
    }

    private void loadUserLocalMessages(String path) throws IOException
    {
        if (path == null)
            throw new IllegalArgumentException();

        userLocalMessages = null;

        File file = new File(getDataFolder(), path);

        if (!file.isFile())
            return;

        try (InputStream is = new FileInputStream(file))
        {
            userLocalMessages = new PropertyResourceBundle(is);
        }
    }
    
    public static String getMessage(String label)
    {
        if (label == null)
            throw new IllegalArgumentException();
        
        if (getInstance() == null)
            return label;
        
        String message = null;
        
        try
        {
            if (getInstance().packageLocalMessages != null
                    && getInstance().packageLocalMessages.containsKey(label))
            {
                message = getInstance().packageLocalMessages.getString(label);
            }
            
            if (getInstance().userGlobalMessages != null
                    && getInstance().userGlobalMessages.containsKey(label))
            {
                message = getInstance().userGlobalMessages.getString(label);
            }
            
            if (getInstance().userLocalMessages != null
                    && getInstance().userLocalMessages.containsKey(label))
            {
                message = getInstance().userLocalMessages.getString(label);
            }
        }
        catch (ClassCastException ex)
        {
        }
        
        if (message == null)
            return label;
        
        return getInstance().replaceGlobalTokens(
                ChatColor.translateAlternateColorCodes('&', message)
        );
    }
    
    public String replaceGlobalTokens(String message)
    {
        if (message == null)
            throw new IllegalArgumentException();
        
        message = message.replace("%bukkit_version%",
                Bukkit.getBukkitVersion());
        message = message.replace("%logit_version%",
                LogItPlugin.getInstance().getDescription().getVersion());
        message = message.replace("%server_id%", Bukkit.getServerId());
        message = message.replace("%server_ip%", Bukkit.getIp());
        message = message.replace("%server_motd%", Bukkit.getMotd());
        message = message.replace("%server_name%", Bukkit.getServerName());
        
        return message;
    }
    
    public void loadLibraries() throws FatalReportedException
    {
        File dir = new File(getDataFolder(), "lib");
        
        if (!dir.isDirectory())
            return;
        
        File[] files = dir.listFiles();
        
        for (File file : files)
        {
            if (!file.isFile())
                continue;
            
            if (!file.getName().endsWith(".jar"))
                continue;
            
            loadLibrary(file.getName());
        }
    }
    
    private void loadLibrary(String filename) throws FatalReportedException
    {
        if (isLibraryLoaded(filename))
            return;
        
        try
        {
            File file = new File(getDataFolder(), "lib/" + filename);
            
            if (!file.exists())
                throw new FileNotFoundException();
            
            URLClassLoader classLoader = (URLClassLoader)
                    ClassLoader.getSystemClassLoader();
            URL url = file.toURI().toURL();
            
            if (!Arrays.asList(classLoader.getURLs()).contains(url))
            {
                Method addUrlMethod = URLClassLoader.class.getDeclaredMethod(
                        "addURL", new Class[]{URL.class}
                );
                addUrlMethod.setAccessible(true);
                addUrlMethod.invoke(classLoader, new Object[]{url});
            }
        }
        catch (FileNotFoundException | MalformedURLException ex)
        {
            getLogger().log(Level.SEVERE,
                    "Library " + filename + " was not found");
            disable();
            
            FatalReportedException.throwNew(ex);
        }
        catch (ReflectiveOperationException ex)
        {
            getLogger().log(Level.SEVERE,
                    "Could not load library " + filename, ex);
            disable();
            
            FatalReportedException.throwNew(ex);
        }
    }
    
    public boolean isLibraryLoaded(String filename)
    {
        File file = new File(getDataFolder(), "lib/" + filename);
        URLClassLoader classLoader = (URLClassLoader)
                ClassLoader.getSystemClassLoader();
        URL url;
        
        try
        {
            url = file.toURI().toURL();
        }
        catch (MalformedURLException ex)
        {
            return false;
        }
        
        return Arrays.asList(classLoader.getURLs()).contains(url);
    }
    
    public static String getCraftBukkitVersion()
    {
        String packageName = Bukkit.getServer()
                .getClass().getPackage().getName();
        String[] packageParts = packageName.split("\\.");
        
        return packageParts[packageParts.length - 1];
    }
    
    /* package */ static LogItPlugin getInstance()
    {
        if (instance == null)
        {
            instance = (LogItPlugin)
                    Bukkit.getPluginManager().getPlugin("LogIt");
        }
        
        return instance;
    }
    
    static
    {
        ConfigurationSerialization.registerClass(LocationSerializable.class);
    }
    
    public static final String PACKAGE = "io.github.lucaseasedup.logit";
    
    private static LogItPlugin instance = null;
    private PropertyResourceBundle packageLocalMessages;
    private PropertyResourceBundle userGlobalMessages;
    private PropertyResourceBundle userLocalMessages;
    private LogItCore core;
}
