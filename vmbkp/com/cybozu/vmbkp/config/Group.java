/**
 * @file
 * @brief Group
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.config;

import java.util.logging.Logger;

import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.StringWriter;
import java.io.IOException;

import com.cybozu.vmbkp.config.FormatString;
import com.cybozu.vmbkp.util.Utility;

/**
 * @brief Manage a group, which contains up to two strings.
 */
public class Group
    implements Comparable
{
    private static final Logger logger_
        = Logger.getLogger(Group.class.getName());

    /**
     * First group name.
     * This must be a basic string (Type.BSTRING).
     */
    private String grp0_;

    /**
     * Second group name.
     */
    private String grp1_;

    /**
     * Constructor for a group with two strings.
     */
    public Group(String g0, String g1)
    {
        grp0_ = g0;
        grp1_ = g1;

        assert FormatString.getType(grp0_) == Type.BSTRING;
    }

    /**
     * Constructor for a group with a string.
     */
    public Group(String g0)
    {
        grp0_ = g0;
        grp1_ = null;

        assert FormatString.getType(grp0_) == Type.BSTRING;
    }

    /**
     * Get size of group, which is the number of strings inside the group.
     *
     * @return 1 if the group contains only one string,
     *         2 for two strings.
     *         0 may be returned but such group is not allowd as a group.
     */
    public int size()
    {
        int size = 0;

        if (grp0_ != null) { size ++; }
        if (grp1_ != null) { size ++; }

        return size;
    }

    /**
     * Get the first string in the group.
     *
     * @return first group string.
     */
    public String getG0()
    {
        return grp0_;
    }

    /**
     * Get the second string in the group.
     *
     * @return second group string.
     */
    public String getG1()
    {
        return grp1_;
    }

    /**
     * Comparator
     */
    public int compareTo(Object obj)
    {
        int ret = compareToImpl(obj);

        logger_.fine(String.format
                     ("compare: %s %s %s\n",
                      this.toString(),
                      compToString(ret),
                      ((Group) obj).toString()));

        return ret;
    }

    /**
     * Convert result of compareTo() to String.
     *
     * @return order operator by string.
     */
    private String compToString(int comp)
    {
        String ret = "?";
        if      (comp == 0) { ret = "=="; }
        else if (comp <  0) { ret = "<"; }
        else                { ret = ">"; }
        return ret;
    }

    /**
     * Implementation of the comparator.
     *
     * @param obj right.
     * @return -1 for (<), 0 for (==), 1 for (>).
     */
    private int compareToImpl(Object obj)
    {
        Group rhs = (Group)obj;
        
        int size1 = this.size();
        int size2 = rhs.size();

        if (size1 == 0 && size2 == 0) { return 0; }
        if (size1 == 0)               { return -1; }
        if (size2 == 0)               { return 1; }

        assert (size1 >= 1 && size2 >= 1);
        int cmpG0 = this.grp0_.compareTo(rhs.grp0_);
            
        if (size1 == 1 && size2 == 1) {
            return cmpG0;
        }
        if (size1 == 1 && size2 == 2) {
            if (cmpG0 != 0) { return cmpG0; }
            else { return -1; }
        }
        if (size1 == 2 && size2 == 1) {
            if (cmpG0 != 0) { return cmpG0; }
            else { return 1; }
        }

        assert(size1 == 2 && size2 == 2);
        if (cmpG0 != 0) {
            return cmpG0;
        } else {
            int cmpG1 = this.grp1_.compareTo(rhs.grp1_);
            return cmpG1;
        }
    }

    /**
     * Write the group data as text file format.
     *
     * @param out writer.
     */
    public void write(BufferedWriter out)
        throws IOException
    {
        writeImpl(out, "\n");
    }

    /**
     * Write the group data as text file format with a suffix string.
     */
    private void writeImpl(BufferedWriter out, String suffix)
        throws IOException
    {
        assert (grp0_ != null);

        assert FormatString.getType(grp0_) == Type.BSTRING;

        out.write('[');

        out.write(grp0_);
        
        if (grp1_ != null) {
            out.write(' ');
            out.write(FormatString.toQuatedString(grp1_));
        }

        out.write("]");
        if (suffix != null) {
            out.write(suffix);
        }
    }

    /**
     * Convert to a string.
     *
     * @return Group representation string in succeeded, or null.
     */
    public String toString()
    {
        StringWriter sw = new StringWriter();
        BufferedWriter out = new BufferedWriter(sw);
        try {
            this.writeImpl(out, null);
            out.flush();
            return sw.toString();
            
        } catch (IOException e) {
            logger_.warning(e.toString());
            logger_.info(Utility.toString(e));
            return null;
        }
    }
}
