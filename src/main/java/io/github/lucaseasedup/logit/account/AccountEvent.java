/*
 * AccountEvent.java
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

import java.util.ArrayList;
import java.util.List;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public abstract class AccountEvent extends Event implements Cancellable
{
    public AccountEvent(Account account)
    {
        this.account = account;
        this.successTasks = new ArrayList<>();
        this.failureTasks = new ArrayList<>();
    }
    
    @Override
    public HandlerList getHandlers()
    {
        return handlers;
    }
    
    @Override
    public final boolean isCancelled()
    {
        return cancelled;
    }
    
    @Override
    public final void setCancelled(boolean cancelled)
    {
        this.cancelled = cancelled;
    }
    
    public Account getAccount()
    {
        return account;
    }
    
    /**
     * Equal to <code>getAccount().get("logit.accounts.username")</code>.
     * 
     * @return Username.
     */
    public String getUsername()
    {
        return account.getString("logit.accounts.username");
    }
    
    /**
     * Schedules a task to be executed when the action following
     * this event is succeeds.
     * 
     * @param task the task to be scheduled.
     * 
     * @throws IllegalStateException if tasks has already been executed.
     * @throws NullPointerException  if {@code task} is {@code null}.
     */
    public final void scheduleSuccessTask(Runnable task)
    {
        if (successTasks == null)
            throw new IllegalStateException();
        
        if (task == null)
            throw new NullPointerException();
        
        successTasks.add(task);
    }
    
    /**
     * Schedules a task to be executed when the action following
     * this event is fails.
     * 
     * @param task the task to be scheduled.
     * 
     * @throws IllegalStateException if tasks has already been executed.
     * @throws NullPointerException  if {@code task} is {@code null}.
     */
    public final void scheduleFailureTask(Runnable task)
    {
        if (failureTasks == null)
            throw new IllegalStateException();
        
        if (task == null)
            throw new NullPointerException();
        
        failureTasks.add(task);
    }
    
    /* package */ final void executeSuccessTasks()
    {
        if (successTasks == null)
        {
            invalidateTaskLists();
            
            throw new IllegalStateException();
        }
        
        for (Runnable task : successTasks)
        {
            task.run();
        }
        
        invalidateTaskLists();
    }
    
    /* package */ final void executeFailureTasks()
    {
        if (failureTasks == null)
        {
            invalidateTaskLists();
            
            throw new IllegalStateException();
        }
        
        for (Runnable task : failureTasks)
        {
            task.run();
        }
        
        invalidateTaskLists();
    }
    
    private void invalidateTaskLists()
    {
        successTasks = null;
        failureTasks = null;
    }
    
    public static HandlerList getHandlerList()
    {
        return handlers;
    }
    
    private static final HandlerList handlers = new HandlerList();
    
    private final Account account;
    private boolean cancelled = false;
    private List<Runnable> successTasks;
    private List<Runnable> failureTasks;
}
