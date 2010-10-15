/**
 * @file
 * @brief Utility
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.util;

import java.util.Iterator;
import java.util.logging.Logger;
import java.util.List;
import java.util.LinkedList;
import java.util.TreeSet;
import java.io.File;
import java.io.StringWriter;
import java.io.PrintWriter;

/**
 * @brief Utility class for debug etc.
 */
public class Utility
{
    /**
     * Logger.
     */
    private static final Logger logger_ =
        Logger.getLogger(Utility.class.getName());
    
    /**
     * Concat a list of string.
     *
     * @param list list of strings.
     * @param separator separator string.
     * @param prefix prefix string.
     * @param suffix suffix string.
     */
    public static String concat(Iterable<String> list,
                                String separator,
                                String prefix, String suffix)
    {
        if (list == null) {
            return "";
        }
        StringBuffer sb = new StringBuffer();
        if (prefix != null) {
            sb.append(prefix);
        }
        Iterator<String> ite = list.iterator();
        while (ite.hasNext()) {
            sb.append(ite.next());
            if (separator != null && ite.hasNext()) {
                sb.append(separator);
            }
        }
        if (suffix != null) {
            sb.append(suffix);
        }
        return sb.toString();
    }

    /**
     * Print all contents of a specified list as Iterable<String> type.
     *
     * @param list specified any list of string.
     */
    public static void printList(Iterable<String> list)
    {
        System.out.println(concat(list, "\n", null, null));
    }

    /**
     * Print all contents with a prefix and a suffix.
     */
    public static void printList(Iterable<String> list,
                                 String prefix, String suffix)
    {
        System.out.println(concat(list, "\n", prefix, suffix));
    }

    /**
     * Concat a list of string to a string.
     */
    public static String toString(Iterable<String> list)
    {
        return concat(list, ", ", null, null);
    }

    /**
     * Convert stack trace of Exception to String.
     */
    public static String toString(Exception e)
    {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    /**
     * Convert List<Integer> to List<String>.
     *
     * @param src List of integer.
     * @return List of string. null is skipped.
     */
    public static List<String> toStringList(List<Integer> src)
    {
        List<String> dst = new LinkedList<String>();

        for (Integer i : src) {
            if (i != null) {
                dst.add(i.toString());
            }
        }
        return dst;
    }

    /**
     * Deduplicate Iterable<String>.
     */
    public static List<String> dedup(Iterable<String> list)
    {
        TreeSet<String> set = new TreeSet<String>();

        for (String str: list) {
            set.add(str);
        }

        return new LinkedList<String>(set);
    }

    /**
     * Deduplicate Iterable<String> keeping order.
     */
    public static List<String> dedupKeepingOrder(Iterable<String> list)
    {
        LinkedList<String> ret = new LinkedList<String>();
        
        for (String str: list) {
            if (ret.contains(str) == false) {
                ret.add(str);
            }
        }

        return ret;
    }

    /**
     * Delete the specified directory recursively.
     * It's like 'rm -rf directory'.
     *
     * @param pathFile Target directory to delete.
     * @return True in success to delete, or False.
     */
    public static boolean deleteDirectoryRecursive(File pathFile)
    {
        if (pathFile.isDirectory() == false) {
            return false;
        }            
            
        File[] files = pathFile.listFiles();
        assert files != null;
        boolean ret = false;

        for (int i = 0 ; i < files.length; i ++) {
            if (files[i].isDirectory()) {
                ret = deleteDirectoryRecursive(files[i]);
                if (ret == false) { return false; }
            } else {
                ret = files[i].delete();
                if (ret == false) {
                    logger_.info
                        (String.format
                         ("deleted file %s %s.", files[i].getName(),
                          (ret ? "succeeded" : "failed")));
                }
                if (ret == false) { return false; }
            }
        }
        ret = pathFile.delete();
        if (ret == false) {
            logger_.info
                (String.format("deleted dir %s %s.", pathFile.getName(),
                               (ret ? "succeeded" : "failed")));
        }
        return ret;
    }
    
}
