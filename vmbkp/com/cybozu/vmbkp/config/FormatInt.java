/**
 * @file
 * @brief FormatInt
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.config;

import com.cybozu.vmbkp.config.Parser;

/**
 * @brief Utility for Integer value.
 */
public class FormatInt
{
    /**
     * Check a given string is an integer value or not.
     */
    public static boolean isInteger(String val)
    {
        Parser p = new Parser(val);
        String ret = p.parseInteger();
        if (ret != null && p.isEnd()) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Check a given string can not be represent as int type.
     */
    public static boolean canBeInt(String val)
    {
        if (! isInteger(val)) { return false; }
        return (isMinusOne(val) || toInt(val) != -1);
    }

    /**
     * Check a given string can not be represent as long type.
     */
    public static boolean canBeLong(String val)
    {
        if (! isInteger(val)) { return false; }
        return (isMinusOne(val) || toLong(val) != -1L);
    }

    /**
     * Convert to a long value.
     * !!!Please call isInteger() before calling this method.!!!
     * 
     * @return long value in success, -1 when overflow or val is not an integer.
     */
    public static long toLong(String val)
    {
        if (! isInteger(val)) {
            return (-1L);
        }

        Parser p = new Parser(val);

        String iStr = p.parseNINT();
        if (iStr.charAt(0) == '+') {
            iStr = iStr.substring(1);
        }
        long i = Long.parseLong(iStr);
        
        if (p.isEnd()) {
            /* NINT */
            return i;
        } else {
            /* EXINT */
            Character uChar = p.parseINTUNIT();
            char u = Character.toUpperCase(uChar.charValue());

            long unit = 1L;
            switch (u) {
            case 'K':
                unit = 1024L; break;
            case 'M':
                unit = 1024L * 1024L; break;
            case 'G':
                unit = 1024L * 1024L * 1024L; break;
            case 'T':
                unit = 1024L * 1024L * 1024L * 1024L; break;
            case 'P':
                unit = 1024L * 1024L * 1024L * 1024L * 1024L; break;
            default:
                return (-1L);
            }

            if (! p.isEnd()) { return (-1L); }

            long ret = i * unit;
            /* check overflow */
            if (ret / i == unit && ret / unit == i) {
                return ret;
            } else {
                return (-1L);
            }
        }
    }

    /**
     * Convert to a int value.
     * !!!Please call isInteger() before calling this method.!!!
     * 
     * @return int value in success, -1 when overflow or val is not an integer.
     */
    public static int toInt(String val)
    {
        long lv = toLong(val);
        int iv = (int) lv;
        long lv2 = (long) iv;
        
        if (lv == iv) {
            return iv;
        } else {
            return (-1);
        }
    }

    /**
     * Check a given string is really -1 or not.
     *
     * We can check the result of toInt() or toLong()
     * is an error or really -1.
     */
    public static boolean isMinusOne(String val)
    {
        return val.equals("-1");
    }

    /**
     * Convert long value to ExInt String.
     * (ExInt contains a suffix that shows the unit like k,m,g,t,p.
     */
    public static String toString(long l)
    {
        if (l == 0) {
            return "0";
        }
        
        long base = l;
        int unitScale = 0;
        for (int i = 0; i < 5; i ++) {
            if (base % 1024L == 0) {
                unitScale ++;
                base /= 1024L;
            } else {
                break;
            }
        }
        String baseStr = Long.toString(base);

        /* This procedure should be spirit to a class */
        String unitStr = null;
        switch (unitScale) {
        case 0:
            unitStr = ""; break;
        case 1:
            unitStr = "K"; break;
        case 2:
            unitStr = "M"; break;
        case 3:
            unitStr = "G"; break;
        case 4:
            unitStr = "T"; break;
        case 5:
            unitStr = "P"; break;
        default:
            /* Must not come here! */
            return null;
        }

        return baseStr + unitStr;
    }

    /**
     * Get sign character if exist.
     *
     * @return '+' or '-' or null.
     */
    public static Character getSign(String val)
    {
        Parser p = new Parser(val);
        return p.parseSIGN();
    }

    /**
     * Convert int value to ExInt String.
     */
    public static String toString(int i)
    {
        return toString((long) i);
    }

}
