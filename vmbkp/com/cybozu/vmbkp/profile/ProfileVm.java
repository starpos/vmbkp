/**
 * @file
 * @brief ProfileVm, DescLongComparator
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.profile;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.Comparator;
import java.util.logging.Logger;

import com.cybozu.vmbkp.config.Entry;
import com.cybozu.vmbkp.config.Group;
import com.cybozu.vmbkp.config.FormatBool;
import com.cybozu.vmbkp.config.FormatInt;
import com.cybozu.vmbkp.config.ParseException;
import com.cybozu.vmbkp.config.NotNormalFileException;

import com.cybozu.vmbkp.util.VmInfo;

import com.cybozu.vmbkp.profile.ConfigWrapper;
import com.cybozu.vmbkp.profile.ProfileGeneration;

/**
 * @brief Comparator of long values in descending order.
 */
class DescLongComparator
    implements Comparator<Long>
{
    public int compare(Long o1, Long o2) {
        return (-1) * o1.compareTo(o2);
    }
}

/**
 * @brief Wrapper class to access vmbkp_vm.profile
 */
public class ProfileVm
    extends ConfigWrapper
{
    public static final String FILE_NAME = "vmbkp_vm.profile";

    /**
     * Logger.
     */
    private static final Logger logger_ =
        Logger.getLogger(ProfileVm.class.getName());
    
    /**
     * Group [meta].
     */
    private Group grpMeta_;

    /**
     * Group [index "timestamp_ms-generation"].
     */
    private Group grpTsGen_;

    /**
     * Default constructor. This will create an empty config.
     */
    public ProfileVm()
    {
        super();
        initializeGroups();
    }
    
    /**
     * Constructor reading the specified config file.
     *
     * @param fileName Path to a vmbkp_vm.profile file.
     */
    public ProfileVm(String fileName)
        throws IOException, ParseException, NotNormalFileException
    {
        super(fileName);
        initializeGroups();
    }

    /**
     * Initialize group(s).
     */
    private void initializeGroups()
    {
        /* initialize group(s) */
        grpMeta_ = new Group("meta");
        grpTsGen_ = new Group("index", "timestamp_ms-generation");
    }

    /**
     * Make generation group.
     * @param genId generation id.
     * @return generated group.
     */
    private Group makeGenerationGroup(int genId)
    {
        if (genId < 0) {
            logger_.warning("generation id < 0.");
        }
        return new Group("generation", Integer.toString(genId));
    }

    /**
     * Get status flag of the specified generation.
     *
     * @param genId generation id.
     * @return [generation `genertionId`] status
     */
    public boolean isGenerationSucceeded(int genId)
    {
        Group genGroup = makeGenerationGroup(genId);
        String status = cfg_.getVal(genGroup, "status");
        return (status != null && status.equals("succeeded"));
    }

    /**
     * Set status flag of the specified generation.
     *
     * @param genId generation id.
     * @param isSucceeded specify true if the genration was succeeded, or false.
     */
    public void setIsGenerationSucceeded(int genId, boolean isSucceeded)
    {
        Group genGroup = makeGenerationGroup(genId);
        cfg_.put(genGroup, "status", (isSucceeded ? "succeeded" : "failed"));
    }

    /**
     * Set [meta] name.
     */
    public void setName(String name)
    {
        cfg_.put(grpMeta_, "name", name);
    }

    /**
     * Get [meta] name.
     */
    public String getName()
    {
        return cfg_.getVal(grpMeta_, "name");
    }

    /**
     * Get [meta] moref.
     */
    public String getMoref()
    {
        return cfg_.getVal(grpMeta_, "moref");
    }

    /**
     * Check [meta] is_clean.
     */
    public boolean isClean()
    {
        String isClean = cfg_.getVal(grpMeta_, "is_clean");

        if (isClean == null) {
            logger_.warning("is_clean entry not found.");
            return false;
        }

        if (FormatBool.isBool(isClean) == false) {
            logger_.warning("is_clean entry not bool value.");
            return false;
        }

        if (FormatBool.toBool(isClean) == false) {
            logger_.warning
                (String.format
                 ("is_clean of %s is false. check backup archives.",
                  getMoref()));
            return false;
        }

        /* it is clean. */
        return true;
    }

    /**
     * Initialize [meta] *.
     *
     * @param vmm Initialize metadata of the config
     * for the specified virtual machine.
     */
    public void initializeMetadata(VmInfo vmInfo)
    {
        cfg_.put(grpMeta_, "moref", vmInfo.getMoref());
        cfg_.put(grpMeta_, "name", vmInfo.getName());
        cfg_.put(grpMeta_, "is_clean", "true");
        cfg_.put(grpMeta_, "latest", "-1"); /* latest generation id */
    }

    /**
     * Get succeeded latest generation id.
     *
     * @return generation id if succeeded generation exists, or -2.
     */
    public int getLatestSucceededGenerationId()
    {
        int latestId = getLatestGenerationId();
        if (latestId < 0) {
            return -2;
        }
        if (isGenerationSucceeded(latestId)) {
            return latestId;
        }
        int prevId = getPrevSucceededGenerationId(latestId);
        if (prevId >= 0) {
            return prevId;
        }
        return -2; /* error */
    }

    /**
     * Get the value of [meta] latest.
     */
    public int getLatestGenerationId()
    {
        String genStr = cfg_.getVal(grpMeta_, "latest");

        if (FormatInt.canBeInt(genStr)) {
            return FormatInt.toInt(genStr);
        } else {
            return -2; /* -1 is normal, -2 is abnormal. */
        }
    }

    /**
     * Create new generation id.
     *
     * @param timestampMs timestamp in milliseconds as string.
     * @return new generation id.
     */
    public int createNewGenerationId(String timestampMs)
        throws Exception
    {
        int depGenId = this.getLatestSucceededGenerationId();
        
        int currGenId = this.getLatestGenerationId();
        if (currGenId < -1) { throw new Exception("currGenId < -1."); }
        int newGenId = currGenId + 1; assert newGenId >= 0;

        this.setLatestGenerationId(newGenId);
        this.setIsGenerationSucceeded(newGenId, false);
        this.setTimestampMs(newGenId, timestampMs);
        this.setDependingGenerationId(newGenId, depGenId);
        
        return newGenId;
    }

    /**
     * Set [meta] latest
     *
     * @param newGenId newly created generation id.
     */
    private void setLatestGenerationId(int newGenId)
    {
        cfg_.put(grpMeta_, "latest", Integer.toString(newGenId));
    }

    /**
     * Get id of succeeded previous generation.
     *
     * @param genId base generation id.
     * @return previously succeeded generation if found, or -2.
     */
    public int getPrevSucceededGenerationId(int genId)
    {
        Map<Long,Integer> genMap = this.getGenerationMap();
        ArrayList<Integer> genList = new ArrayList<Integer>(genMap.values());
        
        Integer genIdI = new Integer(genId);

        int curIdx = genList.indexOf(genIdI);
        if (curIdx < 0) { return -2; } /* not found */

        for (int idx = curIdx - 1; idx >= 0; idx --) {
            Integer tmpGenIdI = genList.get(idx);
            int tmpGenId = tmpGenIdI.intValue();

            if (isGenerationSucceeded(tmpGenId)) {
                return tmpGenId;
            }
        }
        return -2;
    }

    /**
     * Get id of succeeded next generation.
     *
     * @param genId base generatino id.
     * @return Newer least succeeded generation if found, or -2.
     */
    public int getNextSucceededGenerationId(int genId)
    {
        Map<Long,Integer> genMap = this.getGenerationMap();
        ArrayList<Integer> genList = new ArrayList<Integer>(genMap.values());
        
        Integer genIdI = new Integer(genId);

        int curIdx = genList.indexOf(genIdI);
        if (curIdx < 0) { return -2; } /* not found */

        for (int idx = curIdx + 1; idx < genList.size(); idx ++) {
            Integer tmpGenIdI = genList.get(idx);
            int tmpGenId = tmpGenIdI.intValue();

            if (isGenerationSucceeded(tmpGenId)) {
                return tmpGenId;
            }
        }

        return -2; /* not found */
    }

    /**
     * Get depending generation id.
     *
     * @param genId generation id.
     * @return if >=0 that is depending on the generation,
     *         -1 then no dependency.
     */
    public int getDependingGenerationId(int genId)
    {
        Group genGroup = makeGenerationGroup(genId);
        String depGenIdStr = cfg_.getVal(genGroup, "depending_generation_id");
        if (depGenIdStr != null && FormatInt.canBeInt(depGenIdStr)) {
            return FormatInt.toInt(depGenIdStr);
        } else {
            return -1;
        }
    }

    /**
     * Set depending generation id.
     *
     * @param genId generation id.
     * @param depGenId genId depends on this.
     */
    public void setDependingGenerationId(int genId, int depGenId)
    {
        Group genGroup = makeGenerationGroup(genId);
        cfg_.put(genGroup, "depending_generation_id",
                 Integer.toString(depGenId));
    }
    
    /**
     * Get id set of old generations.
     *
     * @param keepGenerations The number of generations to keep.
     * @return A set of id of old generations.
     */
    public Set<Integer> getOldGenerationSet(int keepGenerations)
    {
        Set<Integer> ret = new TreeSet<Integer>();
        
        DescLongComparator cmp = new DescLongComparator();
        Map<Long,Integer> map = this.getGenerationMap(cmp);
        
        /* skip first keepGenerations entries */
        int i = 0;
        for (Map.Entry<Long, Integer> e : map.entrySet()) {
            Integer genId = e.getValue();
            if (i >= keepGenerations) {
                ret.add(e.getValue());
            }
            if (isGenerationSucceeded(genId)) {
                i ++;
            }
        }
        return ret;
    }

    /**
     * Get id set of failed generations except for latest generation.
     */
    public List<Integer> getFailedGenerationList()
    {
        List<Integer> list = getGenerationIdList();
        List<Integer> ret = new LinkedList<Integer>();

        int latestId = getLatestGenerationId();
        if (latestId < 0) {
            logger_.warning("LatestId < 0.");
            return ret;
        }
        
        for (Integer genIdI : list) {
            assert genIdI != null;
            int genId = genIdI.intValue();
            
            if (! isGenerationSucceeded(genId)
                && genId != latestId) {

                ret.add(genIdI);
            }
        }
        return ret;
    }

    /**
     * Get the set of all geneartion set.
     * @return map of timestamp -> generation id.
     */
    private Map<Long,Integer> getGenerationMap()
    {
        return getGenerationMap(null);
    }
    
    /**
     * Get the set of all geneartion set.
     * @param cmp Comparator of timestamp.
     * @return map of timestamp -> generation id.
     */
    private Map<Long,Integer> getGenerationMap(Comparator<Long> cmp)
    {
        /* convert to ordered map */
        List<Entry> entryList = cfg_.getAllEntries(grpTsGen_);

        Map<Long, Integer> map;
        if (cmp != null) {
            map = new TreeMap<Long, Integer>(cmp);
        } else {
            map = new TreeMap<Long, Integer>();
        }

        for (Entry entry : entryList) {
            String tsStr = entry.getKey();
            assert FormatInt.canBeLong(tsStr);
            long ts = FormatInt.toLong(tsStr);

            String idStr = entry.getVal();
            assert FormatInt.canBeInt(idStr);
            int id = FormatInt.toInt(idStr);
            
            map.put(new Long(ts), new Integer(id));
        }

        return map;
    }

    /**
     * Get value of [generation `genId`] timestamp_ms.
     */
    public String getTimestampMs(int genId)
    {
        Group genGroup = makeGenerationGroup(genId);
        return cfg_.getVal(genGroup, "timestamp_ms");
    }

    /**
     * Get value of [generation `genid`] timestamp.
     */
    public String getTimestampStr(int genId)
    {
        Group genGroup = makeGenerationGroup(genId);
        return cfg_.getVal(genGroup, "timestamp");
    }
    
    /**
     * Set [generation `genId`] timestamp_ms.
     * and reset [index "timestamp_ms-generation"] data.
     */
    public void setTimestampMs(int genId, String timestampMs)
    {
        String oldTimestampStr = getTimestampMs(genId);
        if (oldTimestampStr != null) {
            logger_.info
                ("delete from [index \"timestamp_ms-generation\"] " +
                 oldTimestampStr);
            cfg_.del(grpTsGen_, oldTimestampStr);
        }

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(Long.valueOf(timestampMs));
        
        Group genGroup = makeGenerationGroup(genId);
        cfg_.put(genGroup, "timestamp", cal.getTime().toString());
        cfg_.put(genGroup, "timestamp_ms", timestampMs);
        cfg_.put(grpTsGen_, timestampMs, Integer.toString(genId));
    }

    /**
     * Set generation information.
     * Please call this after backup of the generation is succeeded.
     */
    public void setGenerationInfo(ProfileGeneration profGen)
    {
        assert profGen != null;

        int genId = profGen.getGenerationId();
        
        Group genGroup = makeGenerationGroup(genId);
        String timestampMs = profGen.getTimestampMs();

        this.setIsGenerationSucceeded(genId, profGen.isSucceeded());
        this.setTimestampMs(genId, timestampMs);
    }

    /**
     * Delete the specified generation information.
     */
    public void delGenerationInfo(int genId)
    {
        /* Delete entry from the secondary index. */
        String timestampMs = this.getTimestampMs(genId);
        if (timestampMs != null) {
            cfg_.del(grpTsGen_, timestampMs);
        }

        /* Delete generation group. */
        Group genGroup = makeGenerationGroup(genId);
        cfg_.delGroup(genGroup);
    }

    /**
     * Get default generation directory path
     * This does not check the path is valid.
     *
     * @return Path string or null in error.
     */
    public String getDefaultGenerationDirectory(int genId)
    {
        if (genId < 0) { return null; }

        /* ProfileVm path */
        String path = getDirectory();
        if (path == null) { return null; }

        return path + "/" +
            Integer.toString(genId);
    }
    
    /**
     * Get default generation profile path for 
     * the specified generationId.
     * This does not check the path is valid.
     *
     * @return Path string or null in error.
     */
    public String getDefaultProfileGenerationPath(int genId)
    {
        String path = getDefaultGenerationDirectory(genId);
        if (path == null) { return null; }
        return path + "/" + ProfileGeneration.FILE_NAME;
    }

    /**
     * Get number of generaton.
     */
    private int getNumOfGeneration()
    {
        Map<Long,Integer> genMap = getGenerationMap();
        return genMap.size();
    }
    
    /**
     * Get number of succeeded generation.
     */
    private int getNumOfSuccceededGeneration()
    {
        Map<Long,Integer> genMap = getGenerationMap();
        ArrayList<Integer> genList =
            new ArrayList<Integer>(genMap.values());

        int count = 0;
        for (Integer i: genList) {
            assert i != null;

            if (isGenerationSucceeded(i)) {
                count ++;
            }
        }
        return count;
    }

    /**
     * Get the list of id of generations.
     *
     * @return list of id of generations.
     *         never return null, never contain null.
     */
    public List<Integer> getGenerationIdList()
    {
        List<Integer> ret = new LinkedList<Integer>();
        Map<Long, Integer> map = getGenerationMap();
        
        for (Integer genId: map.values()) {

            if (genId == null) { continue; }
            ret.add(genId);
        }
        return ret;
    }

    /**
     * Get status string.
     */
    public String getStatusString(boolean isAvailable)
    {
        StringBuffer sb = new StringBuffer();

        if (isAvailable) {
            sb.append(String.format
                      ("[%s][%s]", getMoref(), getName()));
        } else {
            sb.append(String.format
                      ("[(%s)][%s]", getMoref(), getName()));
        }

        int genId = getLatestSucceededGenerationId();
        if (genId < 0) {
            sb.append(" ----------NO_ARCHIVE ----------");
        } else {
            sb.append(String.format
                      ("[Latest %d \"%s\"]", genId, getTimestampStr(genId)));

            int numGen = getNumOfGeneration();
            int numSucceededGen = getNumOfSuccceededGeneration();
            sb.append(String.format
                      ("[Clean %d/%d]", numSucceededGen, numGen));
        }

        return sb.toString();
    }
}
