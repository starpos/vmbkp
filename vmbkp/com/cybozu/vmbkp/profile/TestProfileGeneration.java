/**
 * @file
 * @brief TestProfileGeneration
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.profile;

import java.util.*;

import com.cybozu.vmbkp.util.*;

/**
 * @brief Test of ProfileGeneration class.
 */
public class TestProfileGeneration
{
    static Random rand_;
    static int numDisks_;

    

    public static void main(String[] args)
    {
        rand_ = new Random();

        ProfileGeneration p = testInitializeGeneration();
        try {
            p.write("vm1gen1.profile");
        } catch (Exception e) {
            e.printStackTrace();
        }

        testGetDiskIdList(p);

    }

    private static int makeRandomInt(int max)
    {
        int r = rand_.nextInt();
        if (r < 0) { r = -r; }
        return r % max;
    }

    private static String makeRandomWordString()
    {
        int word = makeRandomInt(65536);
        String wordStr = Integer.toHexString(word);
        int numZeros = 4 - wordStr.length();
        for (int i = 0; i < numZeros; i ++) {
            wordStr = "0" + wordStr;
        }
        /* System.out.println(wordStr + "\n"); */
        
        assert wordStr.length() == 4;
        return wordStr;
    }
    
    private static VmdkInfo makeRandomVmdkInfo()
    {
        String prefix =
            makeRandomWordString() + makeRandomWordString();
        
        String name = "[storage] " + prefix;
        
        String uuid = 
            prefix + "-" +
            makeRandomWordString() + "-" +
            makeRandomWordString() + "-" +
            makeRandomWordString() + "-" +
            makeRandomWordString() +
            makeRandomWordString() +
            makeRandomWordString();

        String changeId = uuid + "/" + Integer.toString(makeRandomInt(100));
        
        int busNumber = makeRandomInt(4);
        int ckey = 1000 + busNumber;

        int unitNumber = makeRandomInt(16);
        int key = 2000 + unitNumber;

        long capacityInKB = (long)makeRandomInt(100) * 1024L * 1024L;

        AdapterType atype = AdapterType.UNKNOWN;
        int type = makeRandomInt(4);
        switch (type) {
        case 0: atype = AdapterType.IDE; break;
        case 1: atype = AdapterType.BUSLOGIC; break;
        case 2: atype = AdapterType.LSILOGIC; break;
        case 3: atype = AdapterType.LSILOGICSAS; break;
        }

        int diskModeInt = makeRandomInt(2);
        String diskMode = null;
        switch (diskModeInt) {
        case 0: diskMode = "persistent"; break;
        case 1: diskMode = "independent_persistent"; break;
        }

        return new VmdkInfo(name, uuid, changeId,
                            key, ckey, capacityInKB, atype,
                            busNumber, unitNumber, diskMode);
    }
    
    public static ProfileGeneration testInitializeGeneration()
    {
        ProfileGeneration profGen = new ProfileGeneration();


        int genId = 0;
        int prevGenId = -1;
        VmInfo vmInfo = new VmInfo("testvm", "vm-10000");
        SnapInfo snapInfo = new SnapInfo("testsnapshot", "snapshot-10001");
//         VmdkInfo = new VmdkInfo("[storage] path/to/vmdk",
//                                 "bbbbbbbb-d289-f632-fbc8-aaaaaaaaaaaa",
//                                 2000, 100, 1024L * 1024L, AdapterType.BUSLOGIC,
//                                 0, 0);

        List<VmdkInfo> vmdkInfoList = new LinkedList<VmdkInfo>();

        numDisks_ = makeRandomInt(15) + 1;
        
        for (int i = 0; i < numDisks_; i ++) {
            vmdkInfoList.add(makeRandomVmdkInfo());
        }

        profGen.initializeGeneration
            (genId, prevGenId, vmInfo, snapInfo,
             vmdkInfoList, Calendar.getInstance(), true);

        return profGen;
    }

    public static void testGetDiskIdList(ProfileGeneration profGen)
    {
        assert profGen.getDiskIdList().size() == numDisks_;
        
        for (Integer diskId: profGen.getDiskIdList()) {

            assert diskId != null;

            String uuid = profGen.getUuid(diskId);
            assert profGen.getDiskIdWithUuid(uuid) == diskId.intValue();

            int ckey = profGen.getControllerDeviceKey(diskId);
            int busNumber = profGen.getBusNumber(ckey);
            assert 1000 + busNumber == ckey;
            
            int key = profGen.getDiskDeviceKey(diskId);
            int unitNumber = profGen.getUnitNumber(diskId);
            assert 2000 + unitNumber == key;
        }
    }
}
