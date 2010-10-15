/**
 * @file
 * @brief GroupMap
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.Map;
import java.util.TreeMap;
import java.util.List;
import java.util.LinkedList;
import java.io.IOException;

/**
 * @brief Manage a set of entryMaps.
 */
public class GroupMap
{
    /**
     * Map of a Group and its contents as an EntryMap.
     */
    private TreeMap<Group, EntryMap> map_;

    /**************************************************************************
     * Public methods.
     **************************************************************************/

    /**
     * Constructor.
     * Make an empty group map.
     */
    public GroupMap()
    {
        map_ = new TreeMap<Group, EntryMap>();
    }

    /**
     * Number of groups in the map.
     */
    public int size()
    {
        return map_.size();
    }

    /**
     * Put an entry with the specified key and value
     * to the specified group.
     */
    public void put(Group group, String key, String val)
    {
        EntryMap entryMap = getEntryMap(group);
        if (entryMap == null) {
            entryMap = new EntryMap();
            putEntryMap(group, entryMap);
        }
        entryMap.put(key, val);
    }

    /**
     * Put the entry to the group.
     */
    public void put(Group group, Entry entry)
    {
        put(group, entry.getKey(), entry.getVal());
    }

    /**
     * Delete an entry with the key in the group.
     */
    public boolean del(Group group, String key)
    {
        EntryMap entryMap = map_.get(group);
        if (entryMap != null) {
            boolean ret = entryMap.del(key);
            return ret;
        } else {
            return false;
        }
    }

    /**
     * Delete the specified group.
     * All entries in the group will be deleted.
     */
    public boolean delGroup(Group group)
    {
        EntryMap entryMap = map_.remove(group);
        return (entryMap != null);
    }

    /**
     * Get an entry with the key in the group.
     */
    public Entry get(Group group, String key)
    {
        EntryMap entryMap = map_.get(group);
        if (entryMap != null) {
            return entryMap.get(key);
        } else {
            return null;
        }
    }

    /**
     * Get a value of the entry with the key in the group.
     */
    public String getVal(Group group, String key)
    {
        EntryMap entryMap = map_.get(group);
        if (entryMap != null) {
            return entryMap.getVal(key);
        } else {
            return null;
        }
    }

    /**
     * Clear the group map. The size becomes 0.
     */
    public void clear()
    {
        map_.clear();
    }

    /**
     * Get all entries in the specified group.
     *
     * @return Never returns null, but emptry list.
     */
    public List<Entry> getAllEntries(Group group)
    {
        EntryMap entryMap = getEntryMap(group);
        if (entryMap == null) {
            return new LinkedList<Entry>();
        } else {
            return entryMap.getAllEntries();
        }
    }
    
    /**
     * Replace all entries of the specified group and
     * replace with the given entryList.
     * Old entries in the group will be deleted.
     */
    public void replaceAllEntries(Group group, List<Entry> entryList)
    {
        EntryMap entryMap = new EntryMap(entryList);
        putEntryMap(group, entryMap);
    }

    /********************************************************************************
     * Private set operations.
     ********************************************************************************/

    /**
     * Get the entry map of the group.
     */
    private EntryMap getEntryMap(Group group)
    {
        return map_.get(group);
    }

    /**
     * Replate the key-value entries in the specified group
     * with the specified entryMap.
     */
    private void putEntryMap(Group group, EntryMap entryMap)
    {
        /* replace the already inserted entryMap, does not merge it. */
        map_.put(group, entryMap);
    }

    /**
     * Merge all entries in the specified entryMap
     * to the specified group's entryMap.
     */
    private void mergeEntryMap(Group group, EntryMap entryMap)
    {
        EntryMap curEntryMap = map_.get(group);
        if (curEntryMap != null) {
            curEntryMap.merge(entryMap);
        } else {
            map_.put(group, entryMap);
        }
    }

    /**
     * Write all groups ans their child entries with the text file format.
     */
    public void write(BufferedWriter out)
        throws IOException
    {
        for (Map.Entry<Group, EntryMap> ent : map_.entrySet()) {
            Group group = ent.getKey();
            group.write(out);

            EntryMap entryMap = ent.getValue();
            if (entryMap != null) {
                entryMap.write(out);
            }
        }
    }
    
}
