package io.github.lucaseasedup.logit.command.wizard;

import static io.github.lucaseasedup.logit.message.MessageHelper.t;
import io.github.lucaseasedup.logit.account.Account;
import io.github.lucaseasedup.logit.common.FatalReportedException;
import io.github.lucaseasedup.logit.common.ReportedException;
import io.github.lucaseasedup.logit.config.PropertyType;
import io.github.lucaseasedup.logit.config.validators.StorageTypeValidator;
import io.github.lucaseasedup.logit.storage.SelectorConstant;
import io.github.lucaseasedup.logit.util.PlayerUtils;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ConvertWizard extends Wizard
{
    public ConvertWizard(CommandSender sender)
    {
        super(sender, Step.WELCOME);
    }
    
    @Override
    protected void onCreate()
    {
        if (getSender() instanceof Player)
        {
            sendMessage("");
        }
        
        sendMessage(t("wizard.convert.welcome"));
        sendMessage(t("wizard.orangeHorizontalLine"));
        sendMessage(t("wizard.convert.welcomeChoice"));
        
        updateStep(Step.WELCOME_CHOICE);
    }
    
    @Override
    protected void onMessage(String message)
    {
        if (getCurrentStep() == Step.WELCOME_CHOICE)
        {
            if ("proceed".equals(message))
            {
                sendMessage(t("wizard.convert.enterStorageType"));
                updateStep(Step.ENTER_DBTYPE);
            }
            else
            {
                sendMessage(t("wizardCancelled"));
                cancelWizard();
            }
        }
        else if (getCurrentStep() == Step.ENTER_DBTYPE)
        {
            if (!new StorageTypeValidator().validate(
                    "storage.accounts.leading.storageType",
                    PropertyType.STRING,
                    message
            ))
            {
                sendMessage(t("wizard.convert.unknownStorageType")
                        .replace("{0}", message));
                sendMessage(t("wizard.convert.unknownStorageType.tryAgain"));
            }
            else
            {
                dbtype = message;
                
                sendMessage(t("wizard.convert.selectedStorageType")
                        .replace("{0}", message));
                
                switch (dbtype)
                {
                case "sqlite":
                case "h2":
                    sendMessage(t("wizard.convert.enterFilename"));
                    updateStep(Step.ENTER_FILENAME);
                    break;
                    
                case "mysql":
                case "postgresql":
                    sendMessage(t("wizard.convert.enterHost"));
                    updateStep(Step.ENTER_HOST);
                    break;
                    
                case "csv":
                    sendMessage(t("wizard.convert.enterFilename"));
                    updateStep(Step.ENTER_TABLE);
                    break;
                    
                default:
                    throw new RuntimeException("Unexpected dbtype: " + dbtype);
                }
            }
        }
        else if (getCurrentStep() == Step.ENTER_FILENAME)
        {
            filename = message;

            sendMessage(t("wizard.convert.enteredFilename")
                    .replace("{0}", message));
            
            switch (dbtype)
            {
            case "sqlite":
            case "h2":
                sendMessage(t("wizard.convert.enterUnit"));
                updateStep(Step.ENTER_TABLE);
                break;
                
            default:
                throw new RuntimeException("Unexpected dbtype: " + dbtype);
            }
        }
        else if (getCurrentStep() == Step.ENTER_HOST)
        {
            host = message;
            
            sendMessage(t("wizard.convert.enteredHost")
                    .replace("{0}", message));
            
            switch (dbtype)
            {
            case "mysql":
            case "postgresql":
                sendMessage(t("wizard.convert.enterUser"));
                updateStep(Step.ENTER_USER);
                break;
                
            default:
                throw new RuntimeException("Unexpected dbtype: " + dbtype);
            }
        }
        else if (getCurrentStep() == Step.ENTER_USER)
        {
            user = message;
            
            sendMessage(t("wizard.convert.enteredUser")
                    .replace("{0}", message));
            
            switch (dbtype)
            {
            case "mysql":
            case "postgresql":
                sendMessage(t("wizard.convert.enterPassword"));
                updateStep(Step.ENTER_PASSWORD);
                break;
                
            default:
                throw new RuntimeException("Unexpected dbtype: " + dbtype);
            }
        }
        else if (getCurrentStep() == Step.ENTER_PASSWORD)
        {
            password = message;
            
            sendMessage(t("wizard.convert.enteredPassword")
                    .replace("{0}", message.replaceAll(".", "*")));
            
            switch (dbtype)
            {
            case "mysql":
                sendMessage(t("wizard.convert.enterDatabaseName"));
                updateStep(Step.ENTER_DATABASE);
                break;
            
            case "postgresql":
                sendMessage(t("wizard.convert.enterUnit"));
                updateStep(Step.ENTER_TABLE);
                break;
            
            default:
                throw new RuntimeException("Unexpected dbtype: " + dbtype);
            }
        }
        else if (getCurrentStep() == Step.ENTER_DATABASE)
        {
            database = message;
            
            sendMessage(t("wizard.convert.enteredDatabaseName")
                    .replace("{0}", message));
            
            switch (dbtype)
            {
            case "mysql":
                sendMessage(t("wizard.convert.enterUnit"));
                updateStep(Step.ENTER_TABLE);
                break;
                
            default:
                throw new RuntimeException("Unexpected dbtype: " + dbtype);
            }
        }
        else if (getCurrentStep() == Step.ENTER_TABLE)
        {
            table = message;
            
            sendMessage(t("wizard.convert.enteredUnit")
                    .replace("{0}", message));
            sendMessage(t("wizard.convert.copyOrSkip"));
            updateStep(Step.COPY_OR_LEAVE);
        }
        else if (getCurrentStep() == Step.COPY_OR_LEAVE)
        {
            copyAccounts = message.equalsIgnoreCase("copy");
            
            sendMessage(t("wizard.convert.finishChoice"));
            updateStep(Step.FINISH_CHOICE);
        }
        else if (getCurrentStep() == Step.FINISH_CHOICE)
        {
            if ("proceed".equals(message))
            {
                doConversion();
            }
            else
            {
                sendMessage(t("wizardCancelled"));
            }
            
            cancelWizard();
        }
    }
    
    private void doConversion()
    {
        for (Player player : PlayerUtils.getOnlinePlayers())
        {
            player.kickPlayer(t("serverMaintenance"));
        }
        
        getConfig("config.yml")
                .set("storage.accounts.leading.storageType", dbtype);
        
        switch (dbtype)
        {
        case "sqlite":
            getConfig("config.yml")
                    .set("storage.accounts.leading.sqlite.filename", filename);
            break;
            
        case "mysql":
            getConfig("config.yml")
                    .set("storage.accounts.leading.mysql.host", host);
            getConfig("config.yml")
                    .set("storage.accounts.leading.mysql.user", user);
            getConfig("config.yml")
                    .set("storage.accounts.leading.mysql.password", password);
            getConfig("config.yml")
                    .set("storage.accounts.leading.mysql.database", database);
            break;

        case "h2":
            getConfig("config.yml")
                    .set("storage.accounts.leading.h2.filename", filename);
            break;

        case "postgresql":
            getConfig("config.yml")
                    .set("storage.accounts.leading.postgresql.host", host);
            getConfig("config.yml")
                    .set("storage.accounts.leading.postgresql.user", user);
            getConfig("config.yml")
                    .set("storage.accounts.leading.postgresql.password", password);
            
            break;
        }
        
        getConfig("config.yml").set("storage.accounts.leading.unit", table);
        
        try
        {
            ReportedException.incrementRequestCount();
            
            List<Account> accounts = null;
            
            if (copyAccounts)
            {
                accounts = getAccountManager().selectAccounts(
                        keys().getNames(), new SelectorConstant(true)
                );
            }
            
            try
            {
                getCore().restart();
            }
            catch (FatalReportedException ex)
            {
                if (getSender() instanceof Player)
                {
                    sendMessage(t("wizard.convert.fail"));
                }
                
                log(Level.SEVERE, t("wizard.convert.fail.log"), ex);
                
                updateStep(Step.FAIL);
                
                if (getConfig("config.yml").getBoolean("terminateUnsafeServer"))
                {
                    Bukkit.getServer().shutdown();
                }
                
                return;
            }
            
            if (copyAccounts && accounts != null)
            {
                getAccountManager().insertAccounts(
                        accounts.toArray(new Account[accounts.size()])
                );
            }
            
            if (getSender() instanceof Player)
            {
                sendMessage(t("wizard.convert.success"));
            }
            
            log(Level.INFO, t("wizard.convert.success.log"));
            
            updateStep(Step.SUCCESS);
        }
        catch (ReportedException ex)
        {
            if (getSender() instanceof Player)
            {
                sendMessage(t("wizard.convert.fail"));
            }
            
            log(Level.SEVERE, t("wizard.convert.fail.log"));
            
            updateStep(Step.FAIL);
        }
        finally
        {
            ReportedException.decrementRequestCount();
        }
    }
    
    public static enum Step
    {
        WELCOME, WELCOME_CHOICE,
        
        ENTER_DBTYPE, ENTER_FILENAME, ENTER_HOST, ENTER_USER,
        ENTER_PASSWORD, ENTER_DATABASE, ENTER_TABLE, COPY_OR_LEAVE,
        
        FINISH_CHOICE, SUCCESS, FAIL
    }
    
    private String dbtype;
    private String filename;
    private String host;
    private String user;
    private String password;
    private String database;
    private String table;
    private boolean copyAccounts;
}
