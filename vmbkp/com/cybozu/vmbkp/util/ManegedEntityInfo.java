/**
 * @file
 * @brief ManagedEntityInfo
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.util;

/**
 * @brief ManagedEntity information.
 */
public class ManagedEntityInfo
{
    private String name_;
    private String moref_;

    /**
     * Constructor.
     */
    public ManagedEntityInfo(String name, String moref)
    {
        name_ = name;
        moref_ = moref;
    }

    public String getName()
    {
        return name_;
    }
    
    public String getMoref()
    {
        return moref_;
    }
}
