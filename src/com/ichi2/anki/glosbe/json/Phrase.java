package com.ichi2.anki.glosbe.json;


/**
 * @author zaur
 * 
 *      This is one of the classes, automatically generated to transform json replies from glosbe.com
 *
 */

public class Phrase{
        private String languageCode;
        private String text;

        public String getLanguageCode(){
                return this.languageCode;
        }
        public void setLanguageCode(String languageCode){
                this.languageCode = languageCode;
        }
        public String getText(){
                return this.text;
        }
        public void setText(String text){
                this.text = text;
        }
}