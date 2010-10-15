/**
 * @file
 * @brief FormatString, Type
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.config;

import java.util.List;

import com.cybozu.vmbkp.config.Parser;

/**
 * @brief String type.
 */
enum Type {BSTRING, NSTRING, QSTRING, NULLSTRING;}

/**
 * @brief Utility class for BString, NString, and QString.
 */
public class FormatString
{
    /**
     * Get what type of string a specified string is.
     */
    public static Type getType(String str)
    {
        if (str.isEmpty()) {
            return Type.NULLSTRING;
        }
        
        Parser p = new Parser(str);

        /* basic string */
        p.reset();
        if (p.parseBSTRING() != null && p.isEnd()) {
            return Type.BSTRING;
        }

        /* normal string */
        p.reset();
        if (p.parseNSTRING() != null && p.isEnd()) {
            return Type.NSTRING;
        }

        /* quated string */
        return Type.QSTRING;
    }

    /**
     * Convert to output string format.
     * This automatically create quated string if needed.
     */
    public static String toStringAuto(String str)
    {
        if (str.isEmpty()) {
            return toQuatedString(str);
        }
        
        Parser p = new Parser(str);

        /* basic string */
        p.reset();
        if (p.parseBSTRING() != null && p.isEnd()) {
            return str;
        }

        /* normal string */
        p.reset();
        if (p.parseNSTRING() != null && p.isEnd()) {
            return str;
        }

        /* quated string */
        return toQuatedString(str);
    }

    /**
     * Explicitly convert to quated string.
     */
    public static String toQuatedString(String str)
    {
        String ret = str.replaceAll("\"", "\\\\\"");
        return '"' + ret + '"';
    }

    /**
     * Explicitly convert to unquated string.
     */
    public static String toUnquatedString(String str)
    {
        if (str.isEmpty()) { return str; }
        
        Parser p = new Parser(str);
        p.reset();
        if (p.parseQSTRING() != null && p.isEnd()) {
            String ret = str.substring(1, str.length() - 1);
            ret = ret.replaceAll("\\\\\"", "\"");
            return ret;
        } else {
            return str;
        }
    }

    /**
     * Join all elements of a list into a string with a separator.
     */
    public static String join(List<String> strList, String separator)
    {
        StringBuffer sb = new StringBuffer();
        
        for (String str : strList) {
            sb.append(str);
            sb.append(separator);
        }

        return sb.toString();
    }

    /**
     * Convert to Single-quated string.
     */
    public static String toSingleQuatedString(String str)
    {
        return "'" + str.replaceAll("'", "\\\\'") + "'";
    }
}
