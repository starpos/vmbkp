/**
 * @file
 * @brief XmlIndent
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.util;

import java.lang.*;
import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import javax.xml.transform.dom.*;
import org.w3c.dom.*;

import com.cybozu.vmbkp.util.Utility;

/**
 * @brief Manage indent and eol of XML files.
 *
 * Set indent and eol of XML data appropriately
 * for better human reading.
 */
public class XmlIndent
{
    /**
     * Logger.
     */
    private static final Logger logger_ =
        Logger.getLogger(XmlIndent.class.getName());

    /**
     * @brief Options to set indent and end of line of XML.
     */
    private class Opt
    {
        /**
         * Indent size.
         */
        public int indent_ = 4;

        /**
         * Insert eol for each tag.
         */
        public boolean eol_ = true;
    }

    /**
     * members variables.
     */
    private Document doc_ = null;
    private Opt opt_ = null;

    /**
     * Default Constructor.
     * Use parseXmlDoc() after calling this to build internal DOM tree.
     */
    public XmlIndent() { opt_ = new Opt(); }

    /**
     * Read input string and make DOM tree internally.
     *
     * @param xmlString XML data as a String.
     */
    public XmlIndent(String xmlString)
        throws Exception
    {
        opt_ = new Opt();
        ByteArrayInputStream is =
            new ByteArrayInputStream(xmlString.getBytes());
        parseXmlDoc(is);
    }

    /**
     * Read input file and make DOM tree internally.
     *
     * @param file XML file to read.
     */
    public XmlIndent(File file)
        throws Exception
    {
        opt_ = new Opt();
        FileInputStream fis =
            new FileInputStream(file);
        parseXmlDoc(fis);
    }

    /**
     * Parse XML document with a specified input stream.
     *
     * @param is input stream of XML document.
     */
    public void parseXmlDoc(InputStream is) throws Exception
    {
        DocumentBuilderFactory dbfactory =
            DocumentBuilderFactory.newInstance();
        DocumentBuilder builder =
            dbfactory.newDocumentBuilder();
        doc_ = builder.parse(is);
    }

    /**
     * Set indent configuration parameter.
     *
     * @param indent non-negative integer of indent.
     */
    public void setIndent(int indent)
    {
        if (indent < 0) {
            opt_.indent_ = indent;
        }
    }

    /**
     * Fix indent of all elements to appropriate ones.
     */
    public void fixIndent() throws Exception
    {
        //printAllTextField();
        deleteAllTextNodeWithSpace();
        insertAllTextNode();
        modifyIndent();
    }

    /**
     * Delete all text fields with meaninsless charactes only.
     * (space, tab, CR, LF)
     */
    public void deleteIndent()
    {
        opt_.indent_ = 0;
        opt_.eol_ = false;
        deleteAllTextNodeWithSpace();
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
            /* This doesn't work well */
            //tf.setOutputProperty(OutputKeys.INDENT, "yes");
            
            StringWriter sw = new StringWriter();
            tf.transform(new DOMSource(doc_), new StreamResult(sw));
            ret = sw.toString();
            
            /* insert LF code appropriately */
            if (opt_.eol_) {
                ret = ret
                    .replaceAll("/>", "/>\n")
                    .replaceAll("(</[^<>]+>)", "$1\n")
                    .replaceAll("(<[^/][^<>]*[^/]>)", "$1\n")
                    .replaceAll("(<([a-zA-Z0-9:_-]+)[^<>]*>)\\n([^<>]+)(</\\2>)", "$1$3$4");
            }
        } catch (Exception e) {
            logger_.warning(Utility.toString(e));
            return null;
        }
        return ret;
    }

    /**
     * Output XML as a string with eol flag.
     *
     * @param eol true for eol for each tag, false for no eol.
     */
    public String toString(boolean eol)
    {
        opt_.eol_ = eol;
        return toString();
    }

    /**
     * Delete all text node with space or eol.
     */
    private void deleteAllTextNodeWithSpace()
    {
        Node root = doc_.getDocumentElement();
        deleteSpaceTextNodeRecursively(root);
    }
    
    /**
     * Delete space, tab, CR, and LF from XML document.
     * This replace the text fields that contains only the above characters.
     *
     * Such <elem...>text only</elem> element will be not changed.
     */
    private void deleteSpaceTextNodeRecursively(Node e)
    {
        Node child = e.getFirstChild();
        while (child != null) {
            /* call recursively */
            deleteSpaceTextNodeRecursively(child);

            Node next = child.getNextSibling();
            if (!isTextNodeAlone(child) && isSpaceOrEol(child)) {
                e.removeChild(child);
            }
            child = next;
        }
    }

    /**
     * Insert text node to all possible place in the XML tree.
     */
    private void insertAllTextNode()
    {
        Node root = doc_.getDocumentElement();
        insertTextNodeRecursively(root);
    }

    /**
     * Insert text node into all possible space between children.
     * But <elem...>text only</elem> element will be not changed.
     */
    private void insertTextNodeRecursively(Node e)
    {
        if (e == null ||
            isHavingOnlyTextNodeChild(e)) {
            /* don't delete text only node. */
            return;
        }
            
        Node child = e.getFirstChild();
        Node prev = null;
        while (child != null) {
            /* call recursively */
            insertTextNodeRecursively(child);

            Node next = child.getNextSibling();
            if (child.getNodeType() != Node.TEXT_NODE &&
                (prev == null ||
                 prev.getNodeType() != Node.TEXT_NODE)) {
                e.insertBefore(doc_.createTextNode(" "), child);
            }
            prev = child;
            child = next;
        }
        Node lastChild = e.getLastChild();
        if (lastChild != null && lastChild.getNodeType() != Node.TEXT_NODE) {
            e.appendChild(doc_.createTextNode(" "));
        }
    }

    /**
     * Print all text field of XML.
     * This is for debug.
     */
    public void printAllTextField()
    {
        Node root = doc_.getDocumentElement();
        printTextFieldRecursively(root);
    }
    
    /**
     * Print text field of all children recursively.
     */
    private void printTextFieldRecursively(Node e)
    {
        String text = null;
        System.out.printf("[nodetype:%d]", e.getNodeType());

        short ntype = e.getNodeType();
        if (ntype == Node.TEXT_NODE) {
            text = e.getNodeValue();
        }
        if (ntype == Node.ELEMENT_NODE) {
            System.out.printf("[tag:%s]", ((Element) e).getTagName());
        }
        Node parent = e.getParentNode();
        if (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            System.out.printf("[parent:%s]", ((Element) parent).getTagName());
        }
        
        if (text != null) {
            if (isTextNodeAlone(e)) {
                System.out.printf("[JUST_TEXT]");
            }
            if (isSpaceOrEol(e)) {
                System.out.printf("[SPACE]");
            }
            System.out.printf("[%s]\n", text);
        } else {
            System.out.printf("[null]\n");
        }

        /* execute recursively */
        Node child = e.getFirstChild();
        while (child != null) {
            printTextFieldRecursively(child);
            Node next = child.getNextSibling();
            child = next;
        }
    }

    /**
     * Predicate whether the specified node e has just one child of TEXT_NODE.
     * 
     * @param e A node to test.
     * @return true if so, false if not.
     */
    private boolean isHavingOnlyTextNodeChild(Node e)
    {
        Node child = e.getFirstChild();
        if (child != null &&
            child.getNodeType() == Node.TEXT_NODE &&
            child.getNextSibling() == null) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Predicate whether the specified node e has no siblings and it's TEXT_NODE.
     *
     * @param e A node to test.
     * @return true if so, false if not.
     */
    private boolean isTextNodeAlone(Node e)
    {
        Node parent = e.getParentNode();
        if (parent != null) {
            return isHavingOnlyTextNodeChild(parent);
        } else {
            return false;
        }
    }

    /**
     * Check whether the text node contains
     * only space, tab, carriage return, or line feed.
     *
     * @param e target node
     * @return true if so, false if not.
     */
    private boolean isSpaceOrEol(Node e)
    {
        if (e == null ||
            e.getNodeType() != Node.TEXT_NODE) {
            return false;
        }
        String txt = e.getNodeValue();

        if (txt.matches("^[ \\t\\r\\n]+$")) {
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Make space string with the given length.
     */
    private String makeSpaceString(int length)
    {
        StringBuffer sbuf = new StringBuffer(length);
        for (int i = 0; i < length; i ++) {
            sbuf.append(' ');
        }
        return sbuf.toString();
    }

    /**
     * Modify indent of XML tree.
     */
    private void modifyIndent()
    {
        /* modify whole element tree */
        Node root = doc_.getDocumentElement();
        indentAllChildrenOfTextNode(root, 0);
    }
    
    /**
     * Delete all children that are TEXT_NODE.
     * This checks whether each text node contains only space/eol characters.
     *
     * @param e Target node.
     */
    private void indentAllChildrenOfTextNode(Node e, int level)
    {
        if(isHavingOnlyTextNodeChild(e)) {
            /* This must contains the meaningful text.
               Don't delete! */
            return;
        }

        String endIndentText = makeSpaceString(level * opt_.indent_);
        String baseIndentText = makeSpaceString((level + 1) * opt_.indent_);

        Node child = e.getFirstChild();
        while (child != null) {
            Node next = child.getNextSibling();

            String indentText = baseIndentText;
            if (next == null) {
                indentText = endIndentText;
            }

            if (child.getNodeType() == Node.TEXT_NODE &&
                isSpaceOrEol(child)) {
                child.setNodeValue(indentText);
            }
            
            /* call recursively */
            indentAllChildrenOfTextNode(child, level + 1);
            
            child = next;
        }
    }

    /****************************************
     * Static members.
     ****************************************/

    /**
     * flags to exit.
     */
    private static boolean exit_ = false;

    /**
     * main method.
     */
    public static void main(String[] args)
    {
        Opt opt = parseCmdLineOpts(args);

        if (exit_) return;
        
        XmlIndent xmli = new XmlIndent();
        try {
            xmli.parseXmlDoc(System.in);
            xmli.setIndent(opt.indent_);
            xmli.fixIndent();
            System.out.print(xmli.toString(opt.eol_));
        } catch (Exception e) {
            logger_.warning(Utility.toString(e));
        }
    }

    /**
     * parse command line options.
     */
    private static Opt parseCmdLineOpts(String[] args)
    {
        XmlIndent xmli = new XmlIndent();
        Opt opt = xmli.new Opt();

        if (args.length > 0) {
            String cur = null;
            String nxt = null;
            int i = 0;
            cur = args[0];
            while (cur != null) {
                nxt = (i + 1 < args.length ? args[i + 1] : null);

                if (cur.equals("--indent") || cur.equals("-i")) {
                    if (nxt != null) {
                        opt.indent_ = Integer.parseInt(nxt, 10);
                    }
                }
                if (cur.equals("--eol") || cur.equals("-e")) {
                    if (nxt != null && nxt.equals("off")) {
                        opt.eol_ = false;
                    }
                }
                if (cur.equals("--help")) {
                    printHelp();
                }
                ++ i;
                cur = nxt;
            }
        }
        return opt;
    }

    /**
     * print help message to stderr.
     */
    private static void printHelp()
    {
        exit_ = true;
        System.err.printf
            ("usage: java com.xml.XmlIndent " +
             "< inputXml > outputXml [--eol bool] [--indent int]\n" +
             "bool must be on/off\n" +
             "int must be non-negative integer\n" +
             "\n");
    }
    
}
