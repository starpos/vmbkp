/**
 * @file
 * @brief TestParser
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.config;

import java.io.*;
import com.cybozu.vmbkp.config.Parser;

/**
 * @brief Test for Parser class.
 */
public class TestParser
{
    /**
     * Read config data from standard input then
     * write it and its context type to standard output.
     */
    public static void main(String[] args)
    {
        try {
            BufferedReader in = (new BufferedReader
                                 (new InputStreamReader(System.in)));

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println(line); //debug
                Parser p = new Parser(line);
                Context c = p.getContext();

                if (c == null) {
                    System.out.println("NULL");
                } else {

                    switch (c) {
                    case GROUP:
                        System.out.println("GROUP");
                        break;
                    case ENTRY:
                        System.out.println("ENTRY");

                        break;
                    case COMMENT:
                        System.out.println("COMMENT");

                        break;
                    default:
                        System.out.println("Context error!");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
