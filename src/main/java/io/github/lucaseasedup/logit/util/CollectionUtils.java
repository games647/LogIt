package io.github.lucaseasedup.logit.util;

import java.util.Collection;
import java.util.HashSet;

public final class CollectionUtils
{
    private CollectionUtils()
    {
    }
    
    public static boolean containsIgnoreCase(String needle, Collection<String> collection)
    {
        for (String s : collection)
        {
            if (s.equalsIgnoreCase(needle))
            {
                return true;
            }
        }
        
        return false;
    }
    
    public static boolean collectionsMatch(Collection<?> coll1, Collection<?> coll2)
    {
        if (coll1 == null && coll2 == null)
        {
            return true;
        }
        else if (coll1 != null && coll2 != null)
        {
            return new HashSet<>(coll1).equals(new HashSet<>(coll2));
        }
        else
        {
            return false;
        }
    }
    
    public static boolean isSubset(Collection<?> subset, Collection<?> of)
    {
        for (Object obj : subset)
        {
            if (!of.contains(obj))
            {
                return false;
            }
        }
        
        return true;
    }
    
    public static boolean containsDuplicates(Collection<?> coll)
    {
        if (coll == null)
            throw new IllegalArgumentException();

        HashSet<?> set = new HashSet<>(coll);

        return set.size() != coll.size();
    }
    
    public static String toString(Iterable<?> iterable)
    {
        if (iterable == null)
            throw new IllegalArgumentException();
        
        StringBuilder sb = new StringBuilder();
        
        for (Object value : iterable)
        {
            if (sb.length() > 0)
            {
                sb.append(", ");
            }
            
            sb.append(value);
        }
        
        return sb.toString();
    }
}
