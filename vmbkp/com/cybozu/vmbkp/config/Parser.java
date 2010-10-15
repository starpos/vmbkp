/**
 * @file
 * @brief Parser, Context
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
package com.cybozu.vmbkp.config;

import java.util.logging.Logger;
import java.util.LinkedList;

/**
 * @brief A type of string data.
 */
enum Context
{
    GROUP, ENTRY, COMMENT;
}

/**
 * @brief Parser for integer, string, and more.
 *
 * Boolean, Integer, Integer with unit (like 1K, 3M),
 * Basic string, Normal string, Quated string,
 * Group, Entry, Comment.
 */
public class Parser
{
    /**
     * Logger.
     */
    private static final Logger logger_ =
        Logger.getLogger(Parser.class.getName());
    
    /**
     * The target string to parse.
     */
    private final String line_;

    /**
     * Constructor.
     *
     * @param line String to parse.
     */
    public Parser(String line)
    {
        line_ = line;
        reset();
    }

    /**
     * Reset parser.
     */
    public void reset()
    {
        iterInit();
    }

    /**
     * Whether input consumed completely.
     */
    public boolean isEnd()
    {
        return !hasNext();
    }
    
    /**
     * Detect the line is which of group, entry, and comment.
     */
    public Context getContext()
    {
        Group group;
        Entry entry;
        String comment;

        iterInit();
        if ((group = parseGROUP()) != null) {
            return Context.GROUP;
        }
        iterInit();
        if ((entry = parseENTRY()) != null) {
            return Context.ENTRY;
        }
        iterInit();
        if ((comment = parseCOMMENT()) != null) {
            return Context.COMMENT;
        }
        /* empty line or other format */
        return null;
    }
    
    /**************************************************************************
     * Exported parsers.
     **************************************************************************/

    /**
     * Parse a group
     *
     * [group1] or [group1 "group2"]
     */
    public Group parseGROUP()
    {
        String g0, g1;

        save();
        parseSPACES();
        if (!parse('[')) { back(); return null; }
        parseSPACES();
        if ((g0 = parseBSTRING()) == null) { back(); return null; }
        logger_.fine(String.format("***%s***", g0)); /* debug */
        parseSPACES();
        logger_.fine(String.format("idx:%d\n", idx_)); /* debug */
        if ((g1 = parseQSTRING()) != null) { parseSPACES(); }
        if (!parse(']')) { back(); return null; }
        parseSPACES();

        consume(); return new Group(g0, g1);
    }

    /**
     * Parse an entry.
     *
     * Like the following:
     * key = value
     *
     * Currently null value like 'key =' is not allowed.
     * You must specify 'key = ""' for null string.
     */
    public Entry parseENTRY()
    {
        String key, val;

        save();
        parseSPACES();
        if ((key = parseSTRING()) == null) { back(); return null; }
        parseSPACES();
        if (!parse('=')) { back(); return null; }
        parseSPACES();
        if ((val = parseSTRING()) == null) {
            val = "false"; /* Currently default value is set to 'false'. */
            parseSPACES();
        }
        
        consume(); return new Entry(key, val);
    }

    /**
     * Parse a commant string.
     *
     * It begins with '#' or ';' and to the end of line.
     */
    public String parseCOMMENT()
    {
        StringBuffer sb = new StringBuffer();

        save();
        parseSPACES();
        
        if (parse('#')) {
            sb.append(new Character('#'));
        } else if (parse(';')) {
            sb.append(new Character(';'));
        } else {
            back(); return null;
        }

        Character ret;
        while (true) {
            if ((ret = parseSPACE()) != null) { sb.append(ret); }
            else if ((ret = parseQCHAR()) != null) { sb.append(ret); }
            else if (parse('"')) { sb.append(new Character('"')); }
            else { break; }
        }
        consume(); return sb.toString();
    }
    
    /**************************************************************************
     * Iterator-like methods and its managing data.
     **************************************************************************/

    /**
     * Index variable for the parser.
     */
    private int idx_;

    /**
     * Index stack for top-down parsing.
     */
    private LinkedList<Integer> idxStack_;

    /**
     * Initialize the iterator.
     */
    private void iterInit()
    {
        idx_ = 0;
        idxStack_ = new LinkedList<Integer>();
    }

    /**
     * hasNext() method.
     */
    private boolean hasNext()
    {
        return (idx_ < line_.length());
    }

    /**
     * next() method. This does not check overflow.
     * Plaese call hasNext() method before calling this.
     */
    private char next()
    {
        logger_.fine(String.format("idx: %d\n", idx_)); //debug
        char ret = line_.charAt(idx_);
        idx_ ++;
        return ret;
    }
    
    /**************************************************************************
     * Stack structure and methods for layered backtrack.
     **************************************************************************/

    /**
     * Save current iterator to the stack.
     */
    private void save()
    {
        idxStack_.push(new Integer(idx_));
    }

    /**
     * Backtrack to the saved iterator.
     */
    private void back()
    {
        idx_ = idxStack_.pop().intValue();
    }
    
    /**
     * Consume the parser then the saved iterator must be deleted.
     */
    private void consume()
    {
        idxStack_.pop();
    }

    /**************************************************************************
     * Primitive parsers.
     **************************************************************************/

    /**
     * Parse the specified character.
     * @return True in succeeded, or false.
     */
    public boolean parse(char c)
    {
        if (!hasNext()) { return false; }
        save();
        char ret = next();
        if (ret == c) {
            consume(); return true;
        } else {
            back(); return false;
        }
    }

    /**
     * Parse the specified string.
     * @return True in succeeded, or false.
     */
    public boolean parse(String str)
    {
        save();
        for (int i = 0; i < str.length(); i ++) {
            if (!hasNext()) { return false; }
            char ret = next();
            if ((ret != str.charAt(i)) ||
                (i + 1 < str.length() && !hasNext())) {
                back(); return false;
            }
        }
        consume(); return true;
    }
    
    /**************************************************************************
     * User defined parsers.
     *
     * For all parsers, if the parsing failed, return null.
     **************************************************************************/

    /**
     * Parse an uppercase alphabet.
     */
    public Character parseALL()
    {
        if (!hasNext()) { return null; }
        save();
        char ret = next();
        if ('A' <= ret && ret <= 'Z') {
            consume();
            return new Character(ret);
        } else {
            back();
            return null;
        }
    }

    /**
     * Parse a lowercase alphabet.
     */
    public Character parseALS()
    {
        if (!hasNext()) { return null; }
        save();
        char ret = next();
        if ('a' <= ret && ret <= 'z') {
            consume();
            return new Character(ret);
        } else {
            back();
            return null;
        }
    }

    /**
     * Parse an alphabet.
     */
    public Character parseAL()
    {
        Character ret = parseALL();
        if (ret != null) {
            return ret;
        }
        return parseALS();
    }
    
    /**
     * Parse a digit.
     */
    public Character parseDIGIT()
    {
        if (!hasNext()) { return null; }
        save();
        char ret = next();
        if ('0' <= ret && ret <= '9') {
            consume();
            return new Character(ret);
        } else {
            back();
            return null;
        }
    }

    /**
     * Parse a sign character '+' or '-'.
     */
    public Character parseSIGN()
    {
        if (!hasNext()) { return null; }
        save();
        char ret = next();
        if (ret == '+' || ret == '-') {
            consume();
            return new Character(ret);
        } else {
            back();
            return null;
        }
    }

    /**
     * Parse a normal integer number with sign.
     */
    public String parseNINT()
    {
        save();
        StringBuffer sb = new StringBuffer();

        Character sign;
        if ((sign = parseSIGN()) != null) {
            sb.append(sign.charValue());
        }
        Character digit;
        digit = parseDIGIT();
        if (digit != null) {
            sb.append(digit.charValue());
        } else {
            back();
            return null;
        }
        while ((digit = parseDIGIT()) != null) {
            sb.append(digit.charValue());
        }
        consume();
        return sb.toString();
    }

    /**
     * Parse a character as a unit of integer.
     *
     * k: kiro = 1024
     * m: meta = 1024 * k
     * g: giga = 1024 * m
     * t: tera = 1024 * g
     * p: peta = 1024 * t
     */
    public Character parseINTUNIT()
    {
        final char[] set = {'k', 'm', 'g', 't', 'p',
                            'K', 'M', 'G', 'T', 'P'};

        if (!hasNext()) { return null; }
        save();
        char ret = next();
        for (int i = 0; i < set.length; i ++) {
            if (ret == set[i]) {
                consume(); return new Character(ret);
            }
        }

        back(); return null;
    }

    /**
     * Parse an integer with an unit character.
     */
    public String parseEXINT()
    {
        save();
        StringBuffer sb = new StringBuffer();
        
        String ret1 = parseNINT();
        if (ret1 == null) {
            back(); return null;
        }
        sb.append(ret1);
        
        Character ret2 = parseINTUNIT();
        if (ret2 == null) {
            back(); return null;
        }
        sb.append(ret2.charValue());

        consume(); return sb.toString();
    }

    /**
     * Parse an integer with/without an unit character.
     */
    public String parseInteger()
    {
        save();
        String ret;

        ret = parseEXINT();
        if (ret != null) {
            consume(); return ret;
        }

        ret = parseNINT();
        if (ret != null) {
            consume(); return ret;
        }

        back(); return null;
    }

    /**
     * Parse a string that means 'true'.
     */
    public String parseTRUE()
    {
        save();
        if (parse("true") || parse('1') || parse("on")) {
            consume(); return "true";
        } else {
            back(); return null;
        }
    }

    /**
     * Parse a string that means 'false'.
     */
    public String parseFALSE()
    {
        save();
        if (parse("false") || parse('0') || parse("off")) {
            consume(); return "false";
        } else {
            back(); return null;
        }
    }

    /**
     * Parse a string that means boolean value ('true' or 'false').
     */
    public String parseBOOL()
    {
        String ret = parseTRUE();
        if (ret != null) { return ret; }

        ret = parseFALSE();
        if (ret != null) { return ret; }

        return null;
    }

    /**
     * Parse a space character.
     *
     * It does not match end-of-line characters.
     */
    public Character parseSPACE()
    {
        if (!hasNext()) { return null; }
        save();
        char c = next();
        if (c == ' ' || c == '\t') {
            consume(); return new Character(c);
        } else {
            back(); return null;
        }
    }

    /**
     * Parse a string with space characters only.
     */
    public String parseSPACES()
    {
        StringBuffer sb = new StringBuffer();
        
        save();
        Character ret = parseSPACE();
        if (ret == null) {
            back(); return null;
        }
        sb.append(ret.charValue());
        
        while ((ret = parseSPACE()) != null) {
            sb.append(ret.charValue());
        }
        consume(); return sb.toString();
    }

    /**
     * Parse an end-of-line characters.
     *
     * This may not a single character, so the return value type is string.
     */
    public String parseEOL()
    {
        String[] set = {"\r\n", "\r", "\n"};

        for (int i = 0; i < set.length; i ++) {
            if (parse(set[i])) {
                return set[i];
            }
        }
        return null;
    }

    /**
     * Parse a basic character allowed in basic string except alphabet and digit.
     */
    public Character parseBCODE()
    {
        char[] set = {'_', '-'};

        save();
        for (int i = 0; i < set.length; i ++) {
            if (parse(set[i])) {
                consume(); return new Character(set[i]);
            }
        }
        back(); return null;
    }

    /**
     * Parse a character allowed in normal string except alphabet and digit.
     */
    public Character parseCODE()
    {
        char[] set = {'!', '@', '$', '%', '^', '&', '*',
                      '(', ')',  '_', '|', '~',
                      '[', ']', '{', '}',  ':', ',', '.',
                      '<', '>', '/', '?'};

        Character ret = parseSIGN();
        if (ret != null) { return ret; }
        
        save();
        for (int i = 0; i < set.length; i ++) {
            if (parse(set[i])) {
                consume(); return new Character(set[i]);
            }
        }
        back(); return null;
    }

    /**
     * Parse a character allowed in quated string except alphabet and digit.
     */
    public Character parseQCODE()
    {
        Character[] set = {'\'', '\\', '#', ';', '='};

        Character ret = parseCODE();
        if (ret != null) { return ret; }
        ret = parseSPACE();
        if (ret != null) { return ret; }

        save();
        if (parse("\\\"")) {
            consume(); return '"'; /* currently '\"' is converted to '"'. */
        }
        for (int i = 0; i < set.length; i ++) {
            if (parse(set[i])) {
                consume(); return new Character(set[i]);
            }
        }
        back(); return null;
    }

    /**
     * Parse an character allowed in basic string.
     */
    public Character parseBCHAR()
    {
        Character ret; 
        if ((ret = parseAL()) != null) { return ret; }
        if ((ret = parseDIGIT()) != null) { return ret; }
        if ((ret = parseBCODE()) != null) { return ret; }
        return null;
    }
    
    /**
     * Parse a basic string.
     */
    public String parseBSTRING()
    {
        StringBuffer sb = new StringBuffer();

        save();
        Character ret;
        if ((ret = parseBCHAR()) == null) { back(); return null; }
        sb.append(new Character(ret));
        while ((ret = parseBCHAR()) != null) {
            sb.append(new Character(ret));
        }
        consume(); return sb.toString();
    }
    
    /**
     * Parse an character allowed in normal string.
     */
    public Character parseNCHAR()
    {
        Character ret; 
        if ((ret = parseAL()) != null) { return ret; }
        if ((ret = parseDIGIT()) != null) { return ret; }
        if ((ret = parseCODE()) != null) { return ret; }
        return null;
    }

    /**
     * Parse a normal string.
     *
     * Space characters is allowd except in the head and the tail of the string.
     */
    public String parseNSTRING()
    {
        StringBuffer sb = new StringBuffer();

        save();
        Character ret = parseNCHAR();
        if (ret == null) { back(); return null; }
        sb.append(ret.charValue());

        while (true) {
            save();
            StringBuffer sb2 = new StringBuffer();
            while ((ret = parseSPACE()) != null) {
                sb2.append(ret.charValue());
            }

            if ((ret = parseNCHAR()) == null) {
                back(); break;
            }
            sb2.append(ret.charValue());
            
            while ((ret = parseNCHAR()) != null) {
                sb2.append(ret.charValue());
            }
            consume(); sb.append(sb2);
        }
        
        consume(); return sb.toString();
    }

    /**
     * Parse an character allowed in quated string.
     */
    public Character parseQCHAR()
    {
        Character ret;
        if ((ret = parseAL()) != null) { return ret; }
        if ((ret = parseDIGIT()) != null) { return ret; }
        if ((ret = parseQCODE()) != null) { return ret; }
        return null;
    }

    /**
     * Parse an quated string.
     *
     * Null string like "" is allowed.
     */
    public String parseQSTRING()
    {
        StringBuffer sb = new StringBuffer();

        Character ret;
        save();
        if (!parse('"')) { back(); return null; }


        while ((ret = parseQCHAR()) != null) {
            sb.append(ret.charValue());
        }

        if (!parse('"')) { back(); return null; }

        consume(); return sb.toString();
    }

    /**
     * Parse a string.
     */
    public String parseSTRING()
    {
        String ret;
        if ((ret = parseQSTRING()) != null) { return ret; }
        if ((ret = parseNSTRING()) != null) { return ret; }
        // basic string is always normal string.

        return null;
    }
}
