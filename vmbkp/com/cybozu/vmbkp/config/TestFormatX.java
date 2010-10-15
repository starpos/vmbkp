/**
 * @file
 * @brief TestFormatX
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.config;

import com.cybozu.vmbkp.config.FormatString;
import com.cybozu.vmbkp.config.FormatBool;
import com.cybozu.vmbkp.config.FormatInt;

/**
 * @brief Test for FormatString.java, FormatInt.java, and FormatBool.java.
 */
public class TestFormatX
{
    /**
     * Just execute to test the classes.
     */
    public static void main(String[] args)
    {
        /*
          string test.
         */
        System.out.printf("---------------------\n" +
                          "string test\n" +
                          "---------------------\n");

        testString("abcde_1234-", Type.BSTRING);
        testString("abcde 1234-", Type.NSTRING);
        testString("abcde\" 1234", Type.QSTRING);
        testString("", Type.NULLSTRING);

        /*
          boolean test.
        */
        System.out.printf("---------------------\n" +
                          " boolean test\n" +
                          "---------------------\n");
        
        testBool("true", true);
        testBool("1", true);
        testBool("on", true);
        testBool("false", true);
        testBool("0", true);
        testBool("off", true);

        /* currently these input fail. */
        testBool("True", false);
        testBool("False", false);
            


        /*
          integer test.
        */
        System.out.printf("---------------------\n" +
                           "integer test\n" +
                           "---------------------\n");
        
        testInt("aaa", false);
        testInt("0", true);
        testInt("-1", true);
        testInt("1", true);
        testInt("233", true);
        testInt("1024", true);
        testInt("4096", true);
        testInt("1048576", true);
        testInt("1048576k", true);
        testInt("1k", true);
        testInt("51k", true);
        testInt("3m", true);
        testInt("1G", true);
        testInt("2G", false);
        testInt("-2G", true);
        testInt("5G", false);
        testInt("1T", false);

        testLong("aaa", false);
        testLong("0", true);
        testLong("-1", true);
        testLong("1", true);
        testLong("233", true);
        testLong("1024", true);
        testLong("4096", true);
        testLong("1048576", true);
        testLong("1048576k", true);
        testLong("1k", true);
        testLong("51k", true);
        testLong("3m", true);
        testLong("1G", true);
        testLong("2G", true);
        testLong("-2G", true);
        testLong("5G", true);
        testLong("1T", true);
        testLong("1P", true);
        testLong("512P", true);
        testLong("1023P", true);
        testLong("1024P", true);
        testLong("8191P", true);
        testLong("8192P", false);
        testLong("-8192P", true);
        testLong("1048576P", false);
    }

    public static void testString(String str, Type type)
    {
        assert FormatString.getType(str) == type;
        System.out.printf("str :%s\n", FormatString.toStringAuto(str));
        System.out.printf("qstr:%s\n", FormatString.toQuatedString(str));
    }

    public static void testBool(String str, boolean isBool)
    {
        boolean b;

        if (FormatBool.isBool(str)) {
            b = FormatBool.toBool(str);
            System.out.printf("%s:%s:%s\n",
                              str,
                              (new Boolean(b)).toString(),
                              FormatBool.toString(b));
        } else {
            System.out.printf("%s: not boolean value.\n", str);
        }

        assert isBool == FormatBool.isBool(str);
    }
    
    public static void testInt(String str, boolean isInt)
    {
        int iVal;

        if (FormatInt.isInteger(str)) {
            iVal = FormatInt.toInt(str);
            System.out.printf("%s:%d:%s:%s\n",
                              str,
                              iVal,
                              FormatInt.toString(iVal),
                              (new Boolean(FormatInt.canBeInt(str))).toString());
        } else {
            System.out.printf("%s: not integer value.\n", str);
        }

        assert isInt == (FormatInt.isInteger(str) && FormatInt.canBeInt(str));
    }

    public static void testLong(String str, boolean isLong)
    {
        long lVal;

        if (FormatInt.isInteger(str)) {
            lVal = FormatInt.toLong(str);
            System.out.printf("%s:%d:%s:%s\n",
                              str,
                              lVal,
                              FormatInt.toString(lVal),
                              (new Boolean(FormatInt.canBeLong(str))).toString());
        } else {
            System.out.printf("%s: not integer value.\n", str);
        }

        assert isLong == (FormatInt.isInteger(str) && FormatInt.canBeLong(str));
    }

}
