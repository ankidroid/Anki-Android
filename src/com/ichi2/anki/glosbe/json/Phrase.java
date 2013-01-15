package com.ichi2.anki.glosbe.json;


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