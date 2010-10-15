/**
 * @file
 * @brief VmInfo
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.util;

/**
 * @brief Virtual machine information.
 */
public class VmInfo
    extends ManagedEntityInfo
{
    /**
     * Constructor.
     */
    public VmInfo(String name, String moref)
    {
        super(name, moref);
    }
}
