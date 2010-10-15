/**
 * @file
 * @brief TestConfigGroup
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.profile;

import java.util.List;

/**
 * @brief Test for ConfigGroup class.
 */
public class TestConfigGroup
{
    private static ProfileAllVm profAllVm_;

    public static void main(String[] args)
    {
        try {
            String cfgGrpPath = "com/profile/test/group.conf";
            String profAllVm_Path = "com/profile/test/allvm.profile";

            profAllVm_ = new ProfileAllVm(profAllVm_Path);
            testGrouping(cfgGrpPath, profAllVm_);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Test code to parse vmbkp_group.conf file.
     */
    public static boolean testGrouping
        (String cfgGroupPath, ProfileAllVm profAllVm_)
    {
        try {
            ConfigGroup cfgGroup =
                new ConfigGroup(cfgGroupPath, profAllVm_);

            List<String> tmpList;

            System.out.println("group-name-1");
            print(cfgGroup.getAllVmMorefListOfGroup("group-name-1"));
            System.out.println("group-name-2");
            print(cfgGroup.getAllVmMorefListOfGroup("group-name-2"));
            System.out.println("group-moref-1");
            print(cfgGroup.getAllVmMorefListOfGroup("group-moref-1"));
            System.out.println("group-moref-2");
            print(cfgGroup.getAllVmMorefListOfGroup("group-moref-2"));
            System.out.println("group-group");
            print(cfgGroup.getAllVmMorefListOfGroup("group-group"));
            System.out.println("group-mix");
            print(cfgGroup.getAllVmMorefListOfGroup("group-mix"));

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Print method.
     */
    private static void print(List<String> morefList)
    {
        for (String moref: morefList) {
            String name = profAllVm_.getVmNameWithMoref(moref);
            System.out.printf("moref: %s, name: %s.\n", moref, name);
        }        
    }
    
}
