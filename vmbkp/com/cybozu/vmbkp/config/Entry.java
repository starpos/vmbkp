/**
 * @file
 * @brief Entry
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

import com.cybozu.vmbkp.config.FormatString;

/**
 * @brief Key-value entry.
 */
public class Entry
    implements Comparable
{
    private String key_;
    private String val_;

    /**
     * Make a entry data.
     * 
     * @param key Key
     * @param val Value
     */
    public Entry(String key, String val)
    {
        key_ = key;
        val_ = val;
    }

    public String getKey()
    {
        return key_;
    }

    public String getVal()
    {
        return val_;
    }

    public void setVal(String val)
    {
        val_ = val;
    }

    public int compareTo(Object obj)
    {
        Entry rhs = (Entry)obj;
        
        int cmpKey = this.key_.compareTo(rhs.key_);
        if (cmpKey != 0) { return cmpKey; }
        
        int cmpVal = this.val_.compareTo(rhs.val_);
        return cmpVal;
    }

    /**
     * Write the entry with the text file format.
     */
    public void write(BufferedWriter out)
        throws IOException
    {
        /* Currently key string can be quated. */
        /* assert (FormatString.getType(key_) == Type.BSTRING); */

        out.write('\t');
        out.write(FormatString.toStringAuto(key_));
        out.write(" = ");
        out.write(FormatString.toStringAuto(val_));
        out.write('\n');
    }
}
