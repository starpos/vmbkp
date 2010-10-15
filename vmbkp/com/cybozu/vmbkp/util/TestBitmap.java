/**
 * @file
 * @brief TestBitmap
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.util;

import com.cybozu.vmbkp.util.VmdkBitmap;

/**
 * @brief Test of VmdkBitmap class.
 *
 * Don't forget execute java with -ea option
 * to enable assertion check.
 */
public class TestBitmap
{
    static final long KILO = 1024L;
    static final long MEGA = 1024L * 1024L;
    static final long GIGA = 1024L * 1024L * 1024L;
    static final long TERA = 1024L * 1024L * 1024L * 1024L;

    public static void main(String[] args)
    {
        /* basic test */
        if (true) {
            VmdkBitmap bmp = new VmdkBitmap(32, 1);

            try {
                bmp.setRangeInBytes(0, 4);
                bmp.setRangeInBytes(16, 8);
            } catch (Exception e) {
                e.printStackTrace();
            }
        
            System.out.println(bmp.toString());

            System.out.println("bytestring begin.");
            byte[] a = bmp.getAsByteString();
            for (int i = 0; i < a.length; i ++) {
                System.out.printf("%02x ", a[i]);
            }
            System.out.println("bytestring end.");
        }

        /* alignment test */
        if (true) {
            VmdkBitmap bmp = new VmdkBitmap(8 * 1024, 1024);

            try {
                bmp.setRangeInBytes(0, 1);
                System.out.println(bmp.toString());
                assert bmp.toString().equals("10000000");
                bmp.clear();

                bmp.setRangeInBytes(2047, 1);
                System.out.println(bmp.toString());
                assert bmp.toString().equals("01000000");
                bmp.clear();

                bmp.setRangeInBytes(4096, 0);
                System.out.println(bmp.toString());
                assert bmp.toString().equals("00000000");
                bmp.clear();

                bmp.setRangeInBytes(4096, 1);
                System.out.println(bmp.toString());
                assert bmp.toString().equals("00001000");
                bmp.clear();
                
                bmp.setRangeInBytes(1024, 1024);
                System.out.println(bmp.toString());
                assert bmp.toString().equals("01000000");
                bmp.clear();

                bmp.setRangeInBytes(1024, 1025);
                System.out.println(bmp.toString());
                assert bmp.toString().equals("01100000");
                bmp.clear();

                bmp.setRangeInBytes(1023, 1025);
                System.out.println(bmp.toString());
                assert bmp.toString().equals("11000000");
                bmp.clear();

                bmp.setRangeInBytes(1023, 1026);
                System.out.println(bmp.toString());
                assert bmp.toString().equals("11100000");
                bmp.clear();

                bmp.setRangeInBytes(1023, 1);
                System.out.println(bmp.toString());
                assert bmp.toString().equals("10000000");
                bmp.clear();

            } catch (Exception e) {
                e.printStackTrace();
            }


        }
        
        /* large bitmap test. */
        if (true) {
            /* 1TB */
            System.out.printf("1TB: %d\n", TERA);
            VmdkBitmap bmp = new VmdkBitmap(TERA);
            
            try {
                bmp.setRangeInBytes(0, MEGA);
                System.out.println(bmp.get(0));
                assert bmp.get(0) == true;
                bmp.clear();

                bmp.setRangeInBytes(GIGA + 1, 2 * MEGA);
                //bmp.setRangeInBytes(GIGA + , 1 * MEGA + 2 * KILO);

                System.out.println(bmp.get(1022)); assert bmp.get(1022) == false;
                System.out.println(bmp.get(1023)); assert bmp.get(1023) == false;
                System.out.println(bmp.get(1024)); assert bmp.get(1024) == true;
                System.out.println(bmp.get(1025)); assert bmp.get(1025) == true;
                System.out.println(bmp.get(1026)); assert bmp.get(1026) == true;
                System.out.println(bmp.get(1027)); assert bmp.get(1027) == false;
                bmp.clear();
                
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }
}
