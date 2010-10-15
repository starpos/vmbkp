/**
 * @file
 * @brief EntryMap
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
 * @brief Manage a set of entries.
 */
public class EntryMap
{
    /* Map of key -> entry (key,val). */
    private TreeMap<String, Entry> map_;

    /**
     * Constructor.
     */
    public EntryMap()
    {
        map_ = new TreeMap<String, Entry>();
    }

    /**
     * Constructor with entry list.
     */
    public EntryMap(List<Entry> entryList)
    {
        map_ = new TreeMap<String, Entry>();
        for (Entry entry : entryList) {
            put(entry);
        }
    }

    /**
     * @return # of entries in the map.
     */
    public int size()
    {
        return map_.size();
    }

    /**
     * Add entry to the map.
     */
    public void put(Entry entry)
    {
        map_.put(entry.getKey(), entry);
    }

    /**
     * Add an entry using the specified key and value.
     */
    public void put(String key, String val)
    {
        map_.put(key, new Entry(key, val));
    }

    /**
     * Delete the entry which has the specified key.
     * @return True if the entry is found and deleted, or false.
     */
    public boolean del(String key)
    {
        Entry entry = map_.remove(key);
        return (entry != null);
    }

    /**
     * Delete the specified entry.
     * Only the key will be used for comparison.
     *
     * @return True if the entry is found and deleted, or false.
     */
    public boolean del(Entry entry)
    {
        Entry entry2 = map_.remove(entry.getKey());
        return (entry2 != null);
    }

    /**
     * Get the entry with the specified key.
     *
     * @return entry if found, or null.
     */
    public Entry get(String key)
    {
        return map_.get(key);
    }

    /**
     * Get the value of the entry with the specified key.
     *
     * @param key Key string.
     * @return Found value string, or null.
     */
    public String getVal(String key)
    {
        Entry entry = map_.get(key);
        if (entry != null) {
            return entry.getVal();
        } else {
            return null;
        }
    }

    /**
     * Merge the specified entries to the map.
     *
     * @param entryMap The map to be merged to this.
     */
    public void merge(EntryMap entryMap)
    {
        map_.putAll(entryMap.map_);
    }

    /**
     * Clear the map. The size of the map becomes 0.
     */
    public void clear()
    {
        map_.clear();
    }

    /**
     * Write all entries with the text file format.
     */
    public void write(BufferedWriter out)
        throws IOException
    {
        for (Map.Entry<String, Entry> ent : map_.entrySet()) {
            Entry entry = ent.getValue();
            if (entry != null) {
                entry.write(out);
            }
        }
    }

    /**
     * Get all entries in the set.
     *
     * @return A list of all entries. This never returns null.
     */
    public List<Entry> getAllEntries()
    {
        List<Entry> ret = new LinkedList<Entry>();
        for (Map.Entry<String, Entry> ent : map_.entrySet()) {
            Entry entry = ent.getValue();
            if (entry != null) {
                ret.add(entry);
            }
        }
        return ret;
    }
    
}
