/**
 * @file
 * @brief ListInfo
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.control;

import com.cybozu.vmbkp.config.FormatInt;

/**
 * @brief Information for list command.
 */
public class ListInfo
{
    /* Filter: Existance in vSphere environment. */
    public int exist;
    public static final int EXIST_NO   = 0;
    public static final int EXIST_YES  = 1;
    public static final int EXIST_BOTH = 2;
    public String existStr;

    /* Filter: Latest generation timestamp is N days/minutes ago.
       
       +N means for greater than N.
       -N means for less than N.
       N means just N.
       
       If both are specified, mtime is used.
    */
    public boolean isMtime;
    public Character signMtime; /* '+' or '-' or null */
    public long mtime; /* absolute value */
    public boolean isMmin;
    public Character signMmin; /* '+' or '-' or null */
    public long mmin; /* absolute value */
    
    /**
     * Constructor.
     * This create required informatino for restore
     * from command line data.
     */
    public ListInfo(VmbkpCommandLine cmdLine)
        throws Exception
    {
        exist = EXIST_YES;
        existStr = "yes";
        isMtime = false;
        signMtime = null;
        mtime = 0L;
        isMmin = false;
        signMmin = null;
        mmin = 0L;

        if (cmdLine.isOption("--exist")) {

            String str = cmdLine.getOptionArgs("--exist").get(0);
            if      (str.equals("yes"))  { exist = EXIST_YES; }
            else if (str.equals("no"))   { exist = EXIST_NO; }
            else if (str.equals("both")) { exist = EXIST_BOTH; }
            else {
                throw new Exception("--exist option requires one of yes, no, or both.");
            }
            existStr = str;
        }

        if (cmdLine.isOption("--mtime")) {

            isMtime = true;
            String str = cmdLine.getOptionArgs("--mtime").get(0);
            if (FormatInt.isInteger(str) && FormatInt.canBeLong(str)) {
                signMtime = FormatInt.getSign(str);
                mtime = FormatInt.toLong(str);
                if (mtime < 0) { mtime = - mtime; }
            } else {
                throw new Exception("--mtime option requires long value with sign.");
            }
        } else if (cmdLine.isOption("--mmin")) {

            String str = cmdLine.getOptionArgs("--mmin").get(0);
            if (FormatInt.isInteger(str) && FormatInt.canBeLong(str)) {
                signMmin = FormatInt.getSign(str);
                mmin = FormatInt.toLong(str);
                if (mmin < 0) { mmin = - mmin; }
            } else {
                throw new Exception("--mtime option requires long value with sign.");
            }
        }
    }

    /**
     * Check time condition.
     *
     * @param tgtMs Target timestamp in milliseconds.
     * @param nowMs Now timestamp in milliseconds.
     * @return True if tgtMs satisfies mtime/mmin condition.
     */
    public boolean isSatisfyTime(long tgtMs, long nowMs)
    {
        boolean isPass = false;
        long baseMs;
        if (isMtime) {
            baseMs = nowMs - (mtime * 24L * 60L * 60L * 1000L);

            if (signMtime.equals('+')) {
                if (baseMs < tgtMs) { isPass = true; }
            } else if (signMtime.equals('-')) {
                if (baseMs > tgtMs) { isPass = true; }
            } else {
                long halfday = 12L * 60L * 60L * 1000L;
                if (baseMs - halfday < tgtMs && tgtMs < baseMs + halfday) {
                    isPass = true;
                }
            }
                        
        } else {
            assert isMmin;
            baseMs = nowMs - (mmin * 60L * 1000L);
                        
            if (signMmin.equals('+')) {
                if (baseMs < tgtMs) { isPass = true; }
            } else if (signMmin.equals('-')) {
                if (baseMs > tgtMs) { isPass = true; }
            } else {
                long halfmin = 30L * 1000L;
                if (baseMs - halfmin < tgtMs && tgtMs < baseMs + halfmin) {
                    isPass = true;
                }
            }
        }
        return isPass;
    }

    /**
     * toString()
     */
    public String toString()
    {
        StringBuffer sb = new StringBuffer();
        sb.append("ListInfo: ");

        sb.append(String.format("[exist %s]", existStr));
        if (isMtime) {
            sb.append(String.format("[mtime %s%d]",
                                    (signMtime == null ? "" : signMtime.toString()),
                                    mtime));
        } else if (isMmin) {
            sb.append(String.format("[mmin %s%d]",
                                    (signMmin == null ? "" : signMmin.toString()),
                                    mmin));
        }
        return sb.toString();
    }
}
