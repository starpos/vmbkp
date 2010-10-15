/**
 * @file
 * @brief TestVmbkpCommandLine
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.control;

import com.cybozu.vmbkp.util.Utility;
import com.cybozu.vmbkp.control.VmbkpCommandLine;

/**
 * @brief Test of VmbkpCommandLine class.
 */
public class TestVmbkpCommandLine
{
    public static void main(String[] args)
    {
        String[] cl;
        cl = new String[] {"update", "--conf", "configfile", "target1", "target2"};
        assert(test(cl));

        cl = new String[] {"backup", "--conf", "configfile", "target1", "target2"};
        assert(test(cl));
        
        cl = new String[] {"restore", "--conf", "configfile", "target1", "target2"};
        assert(test(cl));
        
        cl = new String[] {"check", "--conf", "configfile", "target1", "target2"};
        assert(test(cl));
        
        cl = new String[] {"status", "--conf", "configfile"};
        assert(test(cl));

        cl = new String[] {"--conf", "configfile", "update", "target1", "target2"};
        assert(test(cl));

        cl = new String[] {"--grpconf", "gcfg",
                           "update",
                           "--conf", "cfg",
                           "target1", "target2"};
        assert(test(cl));

        cl = new String[] {"--grpconf", "gcfg",
                           "update",
                           "--help", 
                           "--conf", "cfg",
                           "target1", "target2"};
        assert(test(cl));

        cl = new String[] {"--help"};
        assert(test(cl));

        cl = new String[] {"--conf", "update", "configfile", "target1", "target2"};
        assert(test(cl) == false);
        
        cl = new String[] {"example", "--conf", "configfile", "target1", "target2"};
        assert(test(cl) == false);
        
        cl = new String[] {};
        assert(test(cl) == false);
        
    }

    public static boolean test(String[] args)
    {
//         for (String arg: args) {
//             System.out.printf("%s, ", arg);
//         }
//         System.out.println();
        try {
            VmbkpCommandLine cmdLine = new VmbkpCommandLine(args);

//             Utility.printList(cmdLine.getTargets());

            if (cmdLine.isValid()) {
                return true;
            } else {
                return false;
            }
            
        } catch (Exception e) {
            return false;
        }
    }
    
}
