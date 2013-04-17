package com.ichi2.anki.web;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class UrlTools
{

    public static String encodeUrl(String s)
    {
        try
        {
            return  URLEncoder.encode(s, "utf-8");
        }
        catch (UnsupportedEncodingException e)
        {
            return s;
        } 
    }

}
