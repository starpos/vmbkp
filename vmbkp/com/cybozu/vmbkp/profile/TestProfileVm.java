/**
 * @file
 * @brief TestProfileVm
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.profile;

/**
 * @brief Test of ProfileVm class.
 */
public class TestProfileVm
{
    public static void main(String[] args)
    {
        test1();
        test2();
        test3();
        test4();
        test5();
        test6();
    }


    public static void test1()
    {
        ProfileVm profVm = get("com/profile/test/vm1.profile");
        
        assert profVm.getLatestSucceededGenerationId() == 4;

        assert profVm.getPrevSucceededGenerationId(4) == 3;
        assert profVm.getPrevSucceededGenerationId(3) == 2;
        assert profVm.getPrevSucceededGenerationId(2) == 1;
        assert profVm.getPrevSucceededGenerationId(1) == 0;
        assert profVm.getPrevSucceededGenerationId(0) < 0;

        assert profVm.getNextSucceededGenerationId(0) == 1;
        assert profVm.getNextSucceededGenerationId(1) == 2;
        assert profVm.getNextSucceededGenerationId(2) == 3;
        assert profVm.getNextSucceededGenerationId(3) == 4;
        assert profVm.getNextSucceededGenerationId(4) < 0;

        System.out.println("test1 passed.");
    }

    public static void test2()
    {
        ProfileVm profVm = get("com/profile/test/vm2.profile");
        
        assert profVm.getLatestSucceededGenerationId() == 4;
        assert profVm.getPrevSucceededGenerationId(4) == 2;
        assert profVm.getPrevSucceededGenerationId(3) == 2;
        assert profVm.getPrevSucceededGenerationId(2) == 1;
        assert profVm.getPrevSucceededGenerationId(1) == 0;
        assert profVm.getPrevSucceededGenerationId(0) < 0;

        assert profVm.getNextSucceededGenerationId(0) == 1;
        assert profVm.getNextSucceededGenerationId(1) == 2;
        assert profVm.getNextSucceededGenerationId(2) == 4;
        assert profVm.getNextSucceededGenerationId(3) == 4;
        assert profVm.getNextSucceededGenerationId(4) < 0;

        System.out.println("test2 passed.");
    }

    public static void test3()
    {
        ProfileVm profVm = get("com/profile/test/vm3.profile");
        
        assert profVm.getLatestSucceededGenerationId() == 4;
        assert profVm.getPrevSucceededGenerationId(4) == 3;
        assert profVm.getPrevSucceededGenerationId(3) == 2;
        assert profVm.getPrevSucceededGenerationId(2) == 1;
        assert profVm.getPrevSucceededGenerationId(1) < 0;
        assert profVm.getPrevSucceededGenerationId(0) < 0;

        assert profVm.getNextSucceededGenerationId(0) == 1;
        assert profVm.getNextSucceededGenerationId(1) == 2;
        assert profVm.getNextSucceededGenerationId(2) == 3;
        assert profVm.getNextSucceededGenerationId(3) == 4;
        assert profVm.getNextSucceededGenerationId(4) < 0;

        System.out.println("test3 passed.");
    }
    
    public static void test4()
    {
        ProfileVm profVm = get("com/profile/test/vm4.profile");
        
        assert profVm.getLatestSucceededGenerationId() == 3;
        assert profVm.getPrevSucceededGenerationId(4) == 3;
        assert profVm.getPrevSucceededGenerationId(3) == 2;
        assert profVm.getPrevSucceededGenerationId(2) == 1;
        assert profVm.getPrevSucceededGenerationId(1) == 0;
        assert profVm.getPrevSucceededGenerationId(0) < 0;

        assert profVm.getNextSucceededGenerationId(0) == 1;
        assert profVm.getNextSucceededGenerationId(1) == 2;
        assert profVm.getNextSucceededGenerationId(2) == 3;
        assert profVm.getNextSucceededGenerationId(3) < 0;
        assert profVm.getNextSucceededGenerationId(4) < 0;

        System.out.println("test4 passed.");
    }

    public static void test5()
    {
        ProfileVm profVm = get("com/profile/test/vm5.profile");
        
        assert profVm.getLatestSucceededGenerationId() == 4;
        assert profVm.getPrevSucceededGenerationId(4) == 3;
        assert profVm.getPrevSucceededGenerationId(3) == 0;
        assert profVm.getPrevSucceededGenerationId(2) == 0;
        assert profVm.getPrevSucceededGenerationId(1) == 0;
        assert profVm.getPrevSucceededGenerationId(0) < 0;

        assert profVm.getNextSucceededGenerationId(0) == 3;
        assert profVm.getNextSucceededGenerationId(1) == 3;
        assert profVm.getNextSucceededGenerationId(2) == 3;
        assert profVm.getNextSucceededGenerationId(3) == 4;
        assert profVm.getNextSucceededGenerationId(4) < 0;

        System.out.println("test5 passed.");
    }
    
    public static void test6()
    {
        ProfileVm profVm = get("com/profile/test/vm6.profile");
        
        assert profVm.getLatestSucceededGenerationId() == 0;
        assert profVm.getPrevSucceededGenerationId(1) == 0;

        assert profVm.getNextSucceededGenerationId(0) < 0;

        System.out.println("test6 passed.");
    }

    
    public static ProfileVm get(String filename)
    {
        ProfileVm profVm = null;
        try {
            profVm = new ProfileVm(filename);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return profVm;
    }

}
