/**
 * @file
 * @brief TestOvf
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.util;

import java.io.*;
import java.util.*;

import com.cybozu.vmbkp.util.XmlIndent;
import com.cybozu.vmbkp.util.Utility;

/**
 * @brief Test of ovf convert.
 */
public class TestOvf
{
    /**
     * Just execute this.
     */
    public static void main(String[] args)
    {
        String className = "TestOvf";
        
        if (args.length != 2) {
            System.out.printf("usage: java %s inputFile outputFile\n", className);
        }
        String inputFile = args[0];
        String outputFile = args[1];
        
        try {
            StringBuffer strBuf = new StringBuffer();
            BufferedReader in =
                new BufferedReader
                (new InputStreamReader(new FileInputStream(inputFile)));
            String line;
            while ((line = in.readLine()) != null) { strBuf.append(line); }
            in.close();
            String ovfStr = strBuf.toString();
        
            VmbkpOvf ovf = new VmbkpOvf(ovfStr);

            /* delete disk information in ovf data */
            ovf.deleteFilesInReferences();
            ovf.deleteDisksInDiskSection();

            /*
            List<String> scsiIdList = ovf.deleteScsiControllerDevices();
            for (String id: scsiIdList) {
                System.out.printf("scsiID:%s\n", id); //debug
            }
            ovf.deleteDiskDevices(scsiIdList);
            */

            Set<String> ctrlIdSet
                = ovf.deleteDiskDevicesInHardwareSection();
            /* debug */
            Utility.printList(ctrlIdSet, "ctrlIdSet begin\n", "ctrlIdSet end\n");
            ovf.deleteControllerDevicesWithoutChildInHardwareSection(ctrlIdSet);

            /* fix indent of xml data for human's easy read. */
            XmlIndent xmli = new XmlIndent(ovf.toString());
            xmli.fixIndent();
            
            FileWriter fw = new FileWriter(outputFile);
            fw.write(xmli.toString());
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
