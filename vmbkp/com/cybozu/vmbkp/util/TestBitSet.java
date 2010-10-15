/**
 * @file
 * @brief TestBitSet
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.util;

import java.util.*;
import java.io.*;

/**
 * @brief Test for BitSet class.
 */
public class TestBitSet
{
    public static void main(String[] args)
    {
        BitSet bs = new BitSet(128 * 1024);

        for (int i = 0; i < 128 * 1024; i ++) {
            if (i % 100 == 0) {
                bs.set(i);
            }
        }

        String a = bs.toString();
        System.out.println(a.length());
        System.out.println(bs.length());
    }
}
