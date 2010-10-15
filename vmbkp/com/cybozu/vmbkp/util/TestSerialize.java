/**
 * @file
 * @brief TestSerialize
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.util;

import java.io.*;
import java.util.*;

/**
 * @brief Test for Serializable classes.
 */
public class TestSerialize
    implements java.io.Serializable
{
    public int int1_;
    public String str1_;
    public String[] strs1_;
    public LinkedList<String> listStr_;

    public TestSerialize() {}

    public void print()
    {
        System.out.printf("int1_ : %d\nstr1_ : %s\n", int1_, str1_);

        if (strs1_ != null) {
            for (int i = 0; i < strs1_.length; i ++) {
                System.out.printf("strs1_: %d %s\n", i, strs1_[i]);
            }
        }

        if (listStr_ != null) {
            for (String str: listStr_) {
                System.out.printf("listStr_: %s\n", str);
            }
        }
    }

    public static void testWrite()
    {
        System.out.println("testWrite()");

        TestSerialize a = new TestSerialize();
        a.int1_ = 5;
        a.str1_ = "hoge";
        a.strs1_ = (new String[] {"hoge1", "hoge2", "hoge3"});
        a.listStr_ = new LinkedList<String>();
        a.listStr_.add("hoge4");
        a.listStr_.add("hoge5");
        a.listStr_.add("hoge6");

        TestSerialize b = new TestSerialize();
        b.int1_ = 3;
        b.str1_ = "hogehoge";
        b.strs1_ = null; //(new String[] {});
        b.listStr_ = null; //new LinkedList<String>();
        
        ObjectOutputStream out = null;
        try {
            out = new ObjectOutputStream
                (new BufferedOutputStream
                 (new FileOutputStream("out.data")));

            out.writeObject(a);
            out.writeObject(b);
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void testRead()
    {
        System.out.println("testRead()");
        ObjectInputStream in = null;
        try {
            in = new ObjectInputStream
                (new BufferedInputStream
                 (new FileInputStream("out.data")));
            
            ((TestSerialize)in.readObject()).print();
            ((TestSerialize)in.readObject()).print();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args)
    {
        testWrite();
        testRead();
    }
}
