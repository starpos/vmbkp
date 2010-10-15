/**
 * @file
 * @brief FormatBool
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.config;

import com.cybozu.vmbkp.config.Parser;

/**
 * @brief Utility for boolean value.
 */
public class FormatBool
{
    /**
     * Check a given string is a boolean value or not.
     */
    public static boolean isBool(String val)
    {
        Parser p = new Parser(val);

        if (p.parseBOOL() != null && p.isEnd()) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Convert a given string to a boolean value.
     * !!!Please call isBool() before calling this method.!!!
     *
     * @return boolean value in success, false in failure.
     */
    public static boolean toBool(String val)
    {
        Parser p = new Parser(val);
        
        String t = p.parseTRUE();
        if (t != null && p.isEnd()) {
            return true;
        }
        
        p.reset();
        String f = p.parseFALSE();
        if (f != null && p.isEnd()) {
            return false;
        }

        /* Parse failed. */
        return false;
    }
    
    /**
     * Convert a given boolean value to string.
     */
    public static String toString(boolean b)
    {
        if (b) {
            return "true";
        } else {
            return "false";
        }
    }

}
