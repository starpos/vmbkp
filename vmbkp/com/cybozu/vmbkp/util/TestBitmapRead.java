/**
 * @file
 * @brief TestBitmapRead
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.util;

import java.io.*;

import com.cybozu.vmbkp.util.VmdkBitmap;

/**
 * @brief Test of VmdkBitmap deserialization.
 *
 * Read and print serialized bitmap file.
 */
public class TestBitmapRead
{
    static final long MEGA = 1024L * 1024L;
    public static void main(String[] args)
    {
        try {
            VmdkBitmap bmp = new VmdkBitmap
                ((int) MEGA,
                 new BufferedInputStream(System.in));
            System.out.printf("%s\n", bmp.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
