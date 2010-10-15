/**
 * @file
 * @brief SnapInfo
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.util;

/**
 * @brief Snapshot information.
 */
public class SnapInfo
    extends ManagedEntityInfo
{
    /**
     * Constructor.
     */
    public SnapInfo(String name, String moref)
    {
        super(name, moref);
    }
}
