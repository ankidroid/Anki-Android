package com.ichi2.anki.glosbe.json;

import java.util.List;

/**
 * @author zaur
 * 
 *      This is one of the classes, automatically generated to transform json replies from glosbe.com
 *      
 *      This is the root class, from which response starts.
 *
 */
public class Response
{
    private String dest;
    private String from;
    private String phrase;
    private String result;
    private List<Tuc> tuc;

    public String getDest()
    {
        return this.dest;
    }

    public void setDest(String dest)
    {
        this.dest = dest;
    }

    public String getFrom()
    {
        return this.from;
    }

    public void setFrom(String from)
    {
        this.from = from;
    }

    public String getPhrase()
    {
        return this.phrase;
    }

    public void setPhrase(String phrase)
    {
        this.phrase = phrase;
    }

    public String getResult()
    {
        return this.result;
    }

    public void setResult(String result)
    {
        this.result = result;
    }

    public List<Tuc> getTuc()
    {
        return this.tuc;
    }

    public void setTuc(List<Tuc> tuc)
    {
        this.tuc = tuc;
    }
}
