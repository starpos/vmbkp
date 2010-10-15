/**
 * @file
 * @brief VmbkpOvf
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.util;

import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import org.w3c.dom.Document;

import com.cybozu.vmbkp.util.Utility;

/**
 * @brief ResourceType of VirtualDevice in OVF format.
 */
enum ResourceType
{
    IDE_CONTROLLER, SCSI_CONTROLLER, DISK_DRIVE, UNKNOWN;

    private static final String IDE_CONTROLLER_ID  =  "5";
    private static final String SCSI_CONTROLLER_ID =  "6";
    private static final String DISK_DRIVE_ID      = "17";
    private static final String UNKNOWN_ID         = "unknown";

    public String getId()
    {
        switch (this) {
        case IDE_CONTROLLER:
            return IDE_CONTROLLER_ID;
        case SCSI_CONTROLLER:
            return SCSI_CONTROLLER_ID;
        case DISK_DRIVE:
            return DISK_DRIVE_ID;
        default:
            return UNKNOWN_ID;
        }
    }
}

/**
 * @brief Manage and convert OVF file.
 */
public class VmbkpOvf
{
    /**
     * Logger.
     */
    private static final Logger logger_
        = Logger.getLogger(VmbkpOvf.class.getName());
    
    /**
     * xml document: initialized in the constructor.
     */
    private Document doc_;

    /**
     * read input string as a xml data (exported ovf).
     *
     */
    public VmbkpOvf(String input)
        throws Exception
    {
        //input.getBytes(UTF-8);
        ByteArrayInputStream is = new ByteArrayInputStream(input.getBytes());
        
        DocumentBuilderFactory dbfactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = dbfactory.newDocumentBuilder();
        doc_ = builder.parse(is);
    }
    
    /**
     * change 'ovf:href' attribute in 'References' element.
     */
    public void replaceFileLinkInReferences()
    {
        Element root = doc_.getDocumentElement();
        Element ref = getElementsByTagName(root, "References").get(0);
        List<Element> files = getElementsByTagName(ref, "File");

        int i = 0;
        for (Element elem: files) {
            elem.setAttribute
                ("ovf:href", "disk-" + (new Integer(i)).toString() + ".vmdk");
            i ++;
        }
    }

    /**
     * Called from deleteFilesInReferences() and deleteSisksInDiskSection().
     */
    private void deleteAllNodesWithTagNames(String parentTagName, String childTagName)
    {
        Element root = doc_.getDocumentElement();
        Element parent = getElementsByTagName(root, parentTagName).get(0);
        Node child = parent.getFirstChild();
        while (child != null) {
            Node next = child.getNextSibling();
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                if (((Element) child).getTagName().equals(childTagName)) {
                    parent.removeChild(child);
                }
            }
            child = next;
        }
    }
    
    /**
     * Delete all <File> items from <References> subtree.
     */
    public void deleteFilesInReferences()
    {
        deleteAllNodesWithTagNames("References", "File");
    }

    /**
     * Delete all <Disk> items from <DiskSection> subtree.
     */
    public void deleteDisksInDiskSection()
    {
        deleteAllNodesWithTagNames("DiskSection", "Disk");
    }

    /**
     * Get element <VirtualHardware>.
     */
    private Element getVirtualHardwareElement()
    {
        Element root = doc_.getDocumentElement();
        Element virtualSystem =
            getElementsByTagName(root, "VirtualSystem").get(0);
        Element virtualHardware =
            getElementsByTagName(virtualSystem,
                                 "VirtualHardwareSection").get(0);
        return virtualHardware;
    }

    /**
     * Delete all elements of <Item>
     * that deal with hard disk devices
     * which id is "17" in <rasd:ResourceType> element
     * from <VirtualHardwareSection> section.
     * 
     * @return A set of <rasd:Parent>id</rasd:Parent>
     *   to delete parent controllers.
     *   This must not be null but can be empty.
     */
    public Set<String> deleteDiskDevicesInHardwareSection()
    {
        /* a set to be returned. */
        Set<String> ret = new TreeSet<String>();
        
        Element virtualHardware = getVirtualHardwareElement();

        List<Element> itemList =
            getElementsByTagName(virtualHardware, "Item");

        for (Element item: itemList) {
            
            if ((matchResourceType
                 (item, ResourceType.DISK_DRIVE)) == false) {
                continue;
            }
            
            String parentId = getParentId(item);
            assert parentId != null;
                
            ret.add(parentId);
            virtualHardware.removeChild(item);
            logger_.info("Deleted disk info from ovf file.");
        }
        
        return ret;
    }

    /**
     * Delete all elements of <Item>
     * that deal with IDE/SCSI controller devices
     * which ResourceType id is "5" (IDE) or "6" (SCSI) and
     * have no child in <VirtualHardwareSection>.
     *
     * @param ctrlIdSet A set of controller device id.
     */
    public void deleteControllerDevicesWithoutChildInHardwareSection
        (Set<String> ctrlIdSet)
    {
        Element virtualHardware = getVirtualHardwareElement();
        
        /* For each controller instance id */
        for (String ctrlId : ctrlIdSet) {
            /* check non-disk-drive children exist. */
            Set<String> childSet = getIdSetMatchingParentId(ctrlId);
            if (childSet.isEmpty()) {
                /* delete the controller element */
                Element ctrl = getElementWithInstanceId(ctrlId);
                assert ctrl != null;
                assert (matchResourceType(ctrl, ResourceType.IDE_CONTROLLER) ||
                        matchResourceType(ctrl, ResourceType.SCSI_CONTROLLER));
                virtualHardware.removeChild(ctrl);
            }
        }
    }

    /**
     * Get a set of instance id that the corresponding item contains
     * also parent id that matches the given parentId.
     */
    private Set<String> getIdSetMatchingParentId(String parentId)
    {
        Set<String> ret = new TreeSet<String>();
        if (parentId == null) { return ret; }
        
        Element virtualHardware = getVirtualHardwareElement();
        List<Element> itemList =
            getElementsByTagName(virtualHardware, "Item");

        for (Element item : itemList) {
            String parentId2 = getParentId(item);

            if (parentId2 != null &&
                parentId2.equals(parentId)) {

                String instanceId = getInstanceId(item);
                assert instanceId != null;
                ret.add(instanceId);
            }
        }
        return ret;
    }

    /**
     * Match the given element and its resource type as
     * id described in <rasd:ResourceType>id</rasd:ResourceType>.
     *
     * @param item Element to test.
     * @param type Type string that must be each of ResourceType.
     * @return True when type is matched, or false.
     */
    private boolean matchResourceType(Element item, ResourceType type)
    {
        return matchId(item, "rasd:ResourceType", type.getId());
    }

    /**
     * Match the given element and its id
     *
     * @param item Element to test.
     * @param id Instance Id to match.
     * @return True if matched.
     */
    private boolean matchInstanceId(Element item, String id)
    {
        return matchId(item, "rasd:InstanceID", id);
    }

    /**
     * Match whether the given element that has <tagName>id</tagName> child.
     *
     * @param item Element to test.
     * @param tagName Tag name to match with.
     * @param id Id to match with.
     * @return True if item has a child that is
     *   <tagName>id</tagName>, or false.
     */
    private boolean matchId(Element item, String tagName, String id)
    {
        String tmpId = getId(item, tagName);
        if (tmpId == null) { return false; }

        if (tmpId.equals(id)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Get id in <rasd:Parent> tag in the given element.
     * 
     * @param item Element that contains <rasd:Parent> tag as its child.
     * @return Parent id as a string.
     *   It returns null if <rasd:Parent> child is not found.
     */
    private String getParentId(Element item)
    {
        return getId(item, "rasd:Parent");
    }

    /**
     * Get id in <rasd:InstanceID> tag in the given element.
     */
    private String getInstanceId(Element item)
    {
        return getId(item, "rasd:InstanceID");
    }

    /**
     * Get element that contains <rasd:InstanceID>id</rasd:InstanceID>
     * where the id matches the given one.
     *
     * @param id instance id.
     * @return element object or null.
     *
     */
    private Element getElementWithInstanceId(String id)
    {
        Element virtualHardware = getVirtualHardwareElement();
        List<Element> itemList =
            getElementsByTagName(virtualHardware, "Item");

        for (Element item : itemList) {
            String id2 = getInstanceId(item);
            if (id2 != null) {
                if (id2.equals(id)) {
                    return item;
                }
            }
        }
        return null;
    }
    
    /**
     * Get id from the given child tag of the given item.
     *
     * @param item Element that contains tagName tag as its child.
     * @param tagName Tag name that contains an id.
     * @return Parent id as a string.
     *   It returns null if the tag or id is not found.
     */
    private String getId(Element item, String tagName)
    {
        List<Element> elemList =
            getElementsByTagName(item, tagName);

        if (elemList.isEmpty()) {
            return null;
        }
        Element desc = elemList.get(0);
        Node txt = desc.getFirstChild();

        if (txt != null &&
            txt.getNodeType() == Node.TEXT_NODE) {

            return txt.getNodeValue();
        } else {
            return null;
        }
    }
    
    /**
     * Output XML as a string.
     */
    public String toString()
    {
        String ret = null;
        try {
            TransformerFactory tff = TransformerFactory.newInstance();
            
            Transformer tf = tff.newTransformer();
            tf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            tf.setOutputProperty(OutputKeys.METHOD, "xml");
            //tf.setOutputProperty(OutputKeys.INDENT, "yes"); /* This doesn't work well */
            
            StringWriter sw = new StringWriter();
            tf.transform(new DOMSource(doc_), new StreamResult(sw));
            ret = sw.toString();
            
        } catch (Exception e) {
            logger_.info(Utility.toString(e));
        }
        return ret;
    }

    /**
     * Get number of disks in ovf data.
     *
     * This method counts <File> elements in <Reference> element.
     */
    public int getNumOfDisks()
    {
        Element a = getReferenceElement();
        return getElementsByTagName(a, "File").size();
    }
    
    /**
     * Get <References> element inside Ovf xml.
     */
    private Element getReferenceElement()
    {
        Element root = doc_.getDocumentElement();
        List<Element> refs = getElementsByTagName(root, "References");
        if (refs.size() == 1) {
            return refs.get(0);
        } else {
            logger_.warning("References node must appear only one.");
            return null;
        }
    }

    /**
     * Get all <tag ...> named elemens inside the specified element.
     * A wrapper of Element.getElementsByTagName() method.
     *
     * @param e Search e's children.
     * @param tag TagName of the elements you want.
     * @return A list of matched elements. It never returns 'null'.
     */
    private List<Element> getElementsByTagName(Element e, String tag)
    {
        List<Element> ret = new LinkedList<Element>();
        NodeList list = e.getElementsByTagName(tag);

        int len = list.getLength();
        for (int i = 0; i < len; i ++) {
            ret.add((Element) list.item(i));
        }
        return ret;
    }
}
