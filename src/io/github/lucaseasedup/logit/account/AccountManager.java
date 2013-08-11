/*
 * AccountManager.java
 *
 * Copyright (C) 2012-2013 LucasEasedUp
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.github.lucaseasedup.logit.account;

import static io.github.lucaseasedup.logit.LogItPlugin.getMessage;
import io.github.lucaseasedup.logit.LogItCore;
import io.github.lucaseasedup.logit.LogItCore.HashingAlgorithm;
import io.github.lucaseasedup.logit.LogItCore.IntegrationType;
import io.github.lucaseasedup.logit.LogItCoreObject;
import io.github.lucaseasedup.logit.ReportedException;
import io.github.lucaseasedup.logit.db.Table;
import io.github.lucaseasedup.logit.hash.HashGenerator;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import javax.xml.bind.DatatypeConverter;
import org.bukkit.Bukkit;

/**
 * @author LucasEasedUp
 */
public final class AccountManager extends LogItCoreObject
{
    public AccountManager(LogItCore core, Table accountTable)
    {
        super(core);
        
        this.accountTable = accountTable;
    }
    
    public Set<String> getRegisteredUsernames()
    {
        return accountMap.keySet();
    }
    
    public boolean isRegistered(String username)
    {
        return accountMap.containsKey(username);
    }
    
    /**
     * Creates a new account with the given username and password.
     * <p/>
     * The given password will be hashed using an algorithm specified in the config.
     * 
     * @param username Username.
     * @param password Password.
     * @return True if account has been created,
     *         false if creation has been cancelled by an outside event listener.
     */
    public boolean createAccount(String username, String password)
    {
        if (isRegistered(username))
            throw new RuntimeException("Account already exists.");
        
        HashingAlgorithm algorithm = getCore().getDefaultHashingAlgorithm();
        String salt = HashGenerator.generateSalt(algorithm);
        String hash = getCore().hash(password, salt, algorithm);
        String now = String.valueOf(System.currentTimeMillis() / 1000L);
        
        Map<String, String> properties = new HashMap<>();
        
        properties.put("logit.accounts.username", username.toLowerCase());
        properties.put("logit.accounts.salt", salt);
        properties.put("logit.accounts.password", hash);
        properties.put("logit.accounts.hashing_algorithm", algorithm.encode());
        properties.put("logit.accounts.last_active", now);
        properties.put("logit.accounts.reg_date", now);
        
        AccountEvent evt = new AccountCreateEvent(properties);
        
        Bukkit.getPluginManager().callEvent(evt);
        
        if (evt.isCancelled())
            return false;
        
        try
        {
            accountMap.put(username, new Account(accountTable, properties));
            
            log(Level.FINE, getMessage("CREATE_ACCOUNT_SUCCESS_LOG").replace("%player%", username));
        }
        catch (SQLException ex)
        {
            log(Level.WARNING, getMessage("CREATE_ACCOUNT_FAIL_LOG").replace("%player%", username), ex);
            
            ReportedException.throwNew(ex);
        }
        
        return true;
    }
    
    /**
     * Removes an account with the specified username.
     * <p/>
     * Removing a player's account does not entail logging them out.
     * 
     * @param username Username.
     * @throws AccountNotFoundException Thrown if account does not exist.
     * @return True if account has been removed,
     *         false if removal has been cancelled by an outside event listener.
     */
    public boolean removeAccount(String username)
    {
        if (!isRegistered(username))
            throw new AccountNotFoundException();
        
        Account account = accountMap.get(username);
        AccountEvent evt = new AccountRemoveEvent(account);
        
        Bukkit.getPluginManager().callEvent(evt);
        
        if (evt.isCancelled())
            return false;
        
        try
        {
            accountMap.remove(username);
            
            log(Level.FINE, getMessage("REMOVE_ACCOUNT_SUCCESS_LOG").replace("%player%", username));
        }
        catch (SQLException ex)
        {
            log(Level.WARNING, getMessage("REMOVE_ACCOUNT_FAIL_LOG").replace("%player%", username), ex);
            
            ReportedException.throwNew(ex);
        }
        
        return true;
    }
    
    /**
     * Checks if the given password matches that of account with the specified username.
     * <p/>
     * The given password will be hashed using an algorithm specified
     * in the database or in the config.
     * 
     * @param username Username.
     * @param password Password to check.
     * @throws AccountNotFoundException Thrown if no such account exists.
     * @return True if they match.
     */
    public boolean checkAccountPassword(String username, String password)
    {
        if (!isRegistered(username))
            throw new AccountNotFoundException();
        
        if (accountTable.isColumnDisabled("logit.accounts.password"))
            return true;
        
        Account account = accountMap.get(username);
        HashingAlgorithm algorithm = getCore().getDefaultHashingAlgorithm();
        String userAlgorithm = account.getProperty("logit.accounts.hashing_algorithm");
        
        if (userAlgorithm != null)
        {
            algorithm = HashingAlgorithm.decode(userAlgorithm);
        }
        
        if (!accountTable.isColumnDisabled("logit.accounts.salt"))
        {
            return getCore().checkPassword(password, account.getProperty("logit.accounts.password"),
                    account.getProperty("logit.accounts.salt"), algorithm);
        }
        else
        {
            return getCore().checkPassword(password, account.getProperty("logit.accounts.password"), algorithm);
        }
    }
    
    /**
     * Changes password of an account with the given username.
     * <p/>
     * The given password will be hashed using an algorithm specified in the config.
     * 
     * @param username Username.
     * @param newPassword New password.
     * @return True if password has been changed,
     *         false if operation has been cancelled by an outside event listener.
     */
    public boolean changeAccountPassword(String username, String newPassword)
    {
        if (!isRegistered(username))
            throw new AccountNotFoundException();
        
        Account account = accountMap.get(username);
        AccountEvent evt = new AccountChangePasswordEvent(account, newPassword);
        
        Bukkit.getPluginManager().callEvent(evt);
        
        if (evt.isCancelled())
            return false;
        
        HashingAlgorithm algorithm = getCore().getDefaultHashingAlgorithm();
        String newSalt = HashGenerator.generateSalt(algorithm);
        String newHash = getCore().hash(newPassword, newSalt, algorithm);
        
        try
        {
            account.updateProperty("logit.accounts.salt", newSalt);
            account.updateProperty("logit.accounts.password", newHash);
            account.updateProperty("logit.accounts.hashing_algorithm", algorithm.encode());
            
            log(Level.FINE, getMessage("CHANGE_PASSWORD_SUCCESS_LOG").replace("%player%", username));
        }
        catch (SQLException ex)
        {
            log(Level.WARNING, getMessage("CHANGE_PASSWORD_FAIL_LOG").replace("%player%", username), ex);
            
            ReportedException.throwNew(ex);
        }
        
        return true;
    }
    
    /**
     * Changes e-mail address of an account with the given username.
     * 
     * @param username Username.
     * @param newEmail New e-mail address.
     * @return True if e-mail address has been created,
     *         false if operation has been cancelled by an outside event listener.
     */
    public boolean changeEmail(String username, String newEmail)
    {
        if (!isRegistered(username))
            throw new AccountNotFoundException();
        
        Account account = accountMap.get(username);
        AccountEvent evt = new AccountChangeEmailEvent(account, newEmail);
        
        Bukkit.getPluginManager().callEvent(evt);
        
        if (evt.isCancelled())
            return false;
        
        try
        {
            account.updateProperty("logit.accounts.email", newEmail);
            
            log(Level.FINE, getMessage("CHANGE_EMAIL_SUCCESS_LOG").replace("%player%", username));
        }
        catch (SQLException ex)
        {
            log(Level.WARNING, getMessage("CHANGE_EMAIL_FAIL_LOG").replace("%player%", username), ex);
            
            ReportedException.throwNew(ex);
        }
        
        return true;
    }
    
    public String getEmail(String username)
    {
        return accountMap.get(username).getProperty("logit.accounts.email");
    }
    
    /**
     * Attaches IP address to an account with the specified username.
     * 
     * @param username Username.
     * @param ip IP address.
     * @return True if IP has been attached,
     *         false if operation has been cancelled by an outside event listener.
     * @throws AccountNotFoundException Thrown if the account does not exist.
     */
    public boolean attachIp(String username, String ip)
    {
        if (!isRegistered(username))
            throw new AccountNotFoundException();
        
        Account account = accountMap.get(username);
        AccountEvent evt = new AccountAttachIpEvent(account, ip);
        
        Bukkit.getPluginManager().callEvent(evt);
        
        if (evt.isCancelled())
            return false;
        
        try
        {
            if (getCore().getIntegration() == IntegrationType.PHPBB2)
            {
                try
                {
                    ip = DatatypeConverter.printHexBinary(InetAddress.getByName(ip).getAddress()).toLowerCase();
                }
                catch (UnknownHostException ex)
                {
                }
            }
            
            account.updateProperty("logit.accounts.ip", ip);
            
            log(Level.FINE, getMessage("ATTACH_IP_SUCCESS_LOG").replace("%player%", username)
                    .replace("%ip%", ip));
        }
        catch (SQLException ex)
        {
            log(Level.WARNING, getMessage("ATTACH_IP_FAIL_LOG").replace("%player%", username)
                    .replace("%ip%", ip), ex);
            
            ReportedException.throwNew(ex);
        }
        
        return true;
    }
    
    /**
     * Returns number of accounts with the given IP.
     * If "ip" is null or an empty string, the returned value is 0.
     * 
     * @param ip IP address.
     * @return Number of accounts with the given IP.
     */
    public int countAccountsWithIp(String ip)
    {
        if (ip == null || ip.isEmpty())
            return 0;
        
        int count = 0;
        
        for (Account account : accountMap.values())
        {
            if (account.getProperty("logit.accounts.ip").equalsIgnoreCase(ip))
            {
                count++;
            }
        }
        
        return count;
    }
    
    public int countUniqueIps()
    {
        List<String> ips = new ArrayList<>(accountMap.size());
        
        for (Account account : accountMap.values())
        {
            ips.add(account.getProperty("logit.accounts.ip"));
        }
        
        return new HashSet<String>(ips).size();
    }
    
    public void updateLastActiveDate(String username)
    {
        String now = String.valueOf((int) (System.currentTimeMillis() / 1000L));
        
        try
        {
            accountMap.get(username).updateProperty("logit.accounts.last_active", now);
        }
        catch (SQLException ex)
        {
            log(Level.WARNING, "Could not update last-active date.", ex);
            
            ReportedException.throwNew(ex);
        }
    }
    
    public int getLastActiveDate(String username)
    {
        return Integer.parseInt(accountMap.get(username).getProperty("logit.accounts.last_active"));
    }
    
    public String getAccountProperty(String username, String property)
    {
        Account account = accountMap.get(username);
        
        if (account == null)
            throw new AccountNotFoundException();
        
        return accountMap.get(username).getProperty(property);
    }
    
    public void updateAccountProperty(String username, String property, String value)
    {
        Account account = accountMap.get(username);
        
        if (account == null)
            throw new AccountNotFoundException();
        
        try
        {
            accountMap.get(username).updateProperty(property, value);
        }
        catch (SQLException ex)
        {
            log(Level.WARNING, "Could not update account property: " + property + ".", ex);
            
            ReportedException.throwNew(ex);
        }
    }
    
    public String getAccountPersistence(String username, String key)
    {
        Account account = accountMap.get(username);
        
        if (account == null)
            throw new AccountNotFoundException();
        
        return account.getPersistence(key);
    }
    
    public void updateAccountPersistence(String username, String key, String value)
    {
        Account account = accountMap.get(username);
        
        if (account == null)
            throw new AccountNotFoundException();
        
        try
        {
            account.updatePersistence(key, value);
        }
        catch (SQLException ex)
        {
            log(Level.WARNING, "Could not update account persistance: " + key + ".", ex);
            
            ReportedException.throwNew(ex);
        }
    }
    
    public Collection<Account> getAccounts()
    {
        return accountMap.values();
    }
    
    public int getAccountCount()
    {
        return accountMap.size();
    }
    
    public Table getTable()
    {
        return accountTable;
    }
    
    /**
     * Loads accounts from the database.
     */
    public void loadAccounts()
    {
        Map<String, Account> loadedAccounts = new HashMap<>();
        
        try
        {
            List<Map<String, String>> rs = accountTable.select();
            
            for (Map<String, String> row : rs)
            {
                String username = row.get("logit.accounts.username");
                
                if (username == null || loadedAccounts.containsKey(username))
                    continue;
                
                username = username.toLowerCase();
                
                if (getCore().getIntegration() == IntegrationType.PHPBB2)
                {
                    if (username.equals("anonymous"))
                    {
                        continue;
                    }
                }
                
                Map<String, String> account = new HashMap<>();
                
                for (Entry<String, String> column : row.entrySet())
                {
                    account.put(column.getKey(), column.getValue());
                }
                
                loadedAccounts.put(username, new Account(accountTable, account));
            }
            
            accountMap = new AccountMap(accountTable, loadedAccounts);
            
            log(Level.FINE, getMessage("LOAD_ACCOUNTS_SUCCESS")
                    .replace("%num%", String.valueOf(accountMap.size())));
        }
        catch (SQLException ex)
        {
            log(Level.WARNING, getMessage("LOAD_ACCOUNTS_FAIL"), ex);
            
            ReportedException.throwNew(ex);
        }
    }
    
    private final Table accountTable;
    private AccountMap accountMap = null;
}
