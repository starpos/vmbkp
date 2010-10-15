/**
 * @file
 * @brief Test Logger
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.util;

import java.util.logging.Logger;
import java.util.List;
import java.util.LinkedList;
import com.cybozu.vmbkp.util.Utility;

/**
 * @brief Test of Logger.
 */
public class TestLogger
{
    private static final Logger logger_
        = Logger.getLogger(TestLogger.class.getName());
    
    public static void main(String[] args) 
    {
        System.out.println("begin");
        
        List<String> strList = new LinkedList<String>();
        strList.add("example1");
        strList.add("example2");
        logger_.info(Utility.toString(strList));
        
        try {
            throw new Exception("error example!");
        } catch (Exception e) {
            logger_.warning(e.toString());
            logger_.info(Utility.toString(e));
        }

        System.out.println("finished");
        
    }
}
