/**
 * @file
 * @brief TestConfigLikeGit
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.config;

import java.io.*;
import com.cybozu.vmbkp.config.Parser;

/**
 * @brief Test for ConfigLikeGit class.
 */
public class TestConfigLikeGit
{
    /**
     * Read config data form standard input 
     * then write it to standard output.
     */
    public static void main(String[] args)
    {
        try {
            BufferedReader in = (new BufferedReader
                                 (new InputStreamReader(System.in)));
            
            ConfigLikeGit config = new ConfigLikeGit();

            config.read(in);

            BufferedWriter out = (new BufferedWriter
                                  (new OutputStreamWriter(System.out)));

            config.write(out);
            
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
