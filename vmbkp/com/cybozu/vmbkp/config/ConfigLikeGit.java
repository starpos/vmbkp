/**
 * @file
 * @brief ConfigLikeGit
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.File;
import java.io.IOException;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

/**
 * @brief Easy config data management and serialize/deserialize to/from text file.
 *
 * <pre>
 * It can mangage a map of key string to value string
 * for each group represented string(s).
 *
 * Interface: put/get/del, getValAsInt, getValAsLong.
 * Serialize interface: write/read.
 * Others : clear, getPath, etc.
 *
 * Format:
 *   comment: "[#;].*\n$"
 *   group: "^\[\s*(\w+)(?:\s+(\s+)?\s*\]" -> $1 is g1, $2 is g2.
 *   entry: "\s*(\w+)\s*=\s*(\w+)\s*" -> $1 is key, $2 is val.
 * </pre>
 */
public class ConfigLikeGit
{
    private GroupMap map_;

    /**
     * Constructor
     */
    public ConfigLikeGit()
    {
        map_ = new GroupMap();
    }

    /**
     * Clear whole map.
     * Call this method if reset the object to read another config file.
     */
    public void clear()
    {
        map_.clear();
    }

    /**
     * Get path of the config file if [___tmp___] path_myself is available.
     *
     * @return config path string or null.
     */
    public String getPath()
    {
        Group tmpGroup = new Group("___tmp___");
        return this.getVal(tmpGroup, "path_myself");
    }

    /**
     * Set path of the config file to [___tmp___] path_myself.
     */
    private void setPath(String configFilePath)
    {
        this.put(new Group("___tmp___"), "path_myself", configFilePath);
    }

    
    /********************************************************************************
     * put/del/get
     ********************************************************************************/
    
    public void put(Group group, Entry entry)
    {
        map_.put(group, entry);
    }

    public void put(Group group, String key, String val)
    {
        map_.put(group, key, val);
    }
    
    public boolean del(Group group, String key)
    {
        return map_.del(group, key);
    }

    public boolean delGroup(Group group)
    {
        return map_.delGroup(group);
    }
    
    public Entry get(Group group, String key)
    {
        return map_.get(group, key);
    }

    public String getVal(Group group, String key)
    {
        return map_.getVal(group, key);
    }

    /**
     * Get all entries inside the specified group.
     *
     * @param group
     * @return Never returns null but empty list.
     */
    public List<Entry> getAllEntries(Group group)
    {
        return map_.getAllEntries(group);
    }

    /**
     * Replace all entries in the specified group
     * with the given entryList.
     */
    public void replaceAllEntries(Group group, List<Entry> entryList)
    {
        map_.replaceAllEntries(group, entryList);
    }

    /********************************************************************************
     * Wrapper for getVal() method.
     ********************************************************************************/

    /**
     * Get value of entry with '[group] key' as int value.
     *
     * @param group 
     * @param key 
     * @return -1 if could not be int value.
     */
    public int getValAsInt(Group group, String key)
    {
        String val = this.getVal(group, key);
        
        if (val != null && FormatInt.canBeInt(val)) {
            return FormatInt.toInt(val);
        } else {
            return (-1);
        }
    }

    /**
     * Get value of entry with '[group] key' as long value.
     *
     * @param group 
     * @param key 
     * @return -1L if could not be long value.
     */
    public long getValAsLong(Group group, String key)
    {
        String val = this.getVal(group, key);
        
        if (val != null && FormatInt.canBeLong(val)) {
            return FormatInt.toLong(val);
        } else {
            return (-1L);
        }
    }
    
    /********************************************************************************
     * Serialize/Deserialize to/from text config file.
     ********************************************************************************/
    
    /**
     * Serializer
     */
    public void write(BufferedWriter out)
        throws IOException
    {
        /* [___tmp___] * will not be stored to the file. */
        Group tmpGroup = new Group("___tmp___");
        List<Entry> list = this.getAllEntries(tmpGroup);
        if (list.isEmpty() == false) {
            this.delGroup(tmpGroup);
        }

        map_.write(out);
        out.flush();

        /* restore [___tmp___] *. */
        if (list.isEmpty() == false) {
            this.replaceAllEntries(tmpGroup, list);
        }
    }

    /**
     * Serializer for file.
     */
    public void write(String fileName)
        throws IOException
    {
        /* overwrite the file path. */
        this.setPath(fileName);
        
        BufferedWriter out = (new BufferedWriter (new FileWriter(fileName)));
        write(out);
        out.close();
    }

    /**
     * If key path_myself of group [___tmp___] is not null,
     * use the value as the output filename.
     *
     * @return true in success,
     * false if [___tmp___] path_myself is not defined.
     */
    public boolean write()
        throws IOException
    {
        String configFilePathVm = this.getPath();

        if (configFilePathVm != null) {
            write(configFilePathVm);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Deserializer
     */
    public void read(BufferedReader in)
        throws IOException, ParseException
    {
        Group group = null;
        String line = null;
        while ((line = in.readLine()) != null) {

            Parser parser = new Parser(line);
            Context cxt = parser.getContext();

            if (cxt == Context.GROUP) {
                parser.reset();
                group = parser.parseGROUP();
            } else if (cxt == Context.ENTRY) {
                parser.reset();
                Entry entry = parser.parseENTRY();
                if (group == null) {
                    throw new ParseException();
                } else {
                    /* Put read entry with a specified group. */
                    map_.put(group, entry); 
                }
            } else {
                /* do nothing */
            }
        }
    }

    /**
     * Deserializer for file.
     */
    public void read(String fileName)
        throws IOException, ParseException, NotNormalFileException
    {
        File file = new File(fileName);
        if (file.isFile() == false) {
            throw new NotNormalFileException();
        }
        BufferedReader in = (new BufferedReader (new FileReader(fileName)));
        read(in);
        in.close();

        /* set the file name for temporary use. */
        this.setPath(fileName);
    }
}
