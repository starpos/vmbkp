/**
 * @file
 * @brief TestBitmapWrite
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.util;

import java.io.*;

import com.cybozu.vmbkp.util.VmdkBitmap;

/**
 * @brief Test of VmdkBitmap serialization.
 */
public class TestBitmapWrite
{
    final static long MEGA = 1024L * 1024L;

    public static void main(String[] args)
    {
        try {
            VmdkBitmap bmp = new VmdkBitmap(1025 * MEGA, (int)MEGA);
            bmp.set(5);
            bmp.set(20);
            bmp.writeTo(new FilterOutputStream(System.out));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
