package org.amr.arabic;

/*
 *	Date : 8th of June 2009
 *	the class is Arabic string reshaper Utilities, this class is targeting Android platform
 *
 * 	By		: Amr Ismail Gawish
 *  E-Mail 	: amr.gawish@gmail.com
 *  Web		: http://www.amr-gawish.com
 *  
 *  Updated : 8th of June 2009
 *  Adding comments and Announcing Open Source
 *  
 * Updated: 6th of May 2010
 * Enahancing Functionality by Amine : bakhtout@gmail.com
 *
 * */
import java.util.ArrayList;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.TextView;

/**
 * This class is the main class that is responsible for Reshaping Arabic Sentences and Text
 * Utilities Class to make it easier to deal with Arabic Reshaper Class
 * Wrapper for Arabic Reshaper Class
 * @author Amr Gawish
 */
public class ArabicUtilities {

         /**
	 * the path of teh fonts file must be under assets folder
	 */
	private static final String FONTS_LOCATION_PATH = "fonts/DejaVuSans.ttf";
        static Typeface face ;



	/**
	 * Helper function is to check if the character passed, is Arabic
	 * @param target The Character to check Against
	 * @return true if the Character is Arabic letter, otherwise returns false
	 */
	private static boolean isArabicCharacter(char target){

		//Iterate over the 36 Characters in ARABIC_GLPHIES Matrix
		for(int i = 0; i < ArabicReshaper.ARABIC_GLPHIES.length;i++){
			//Check if the target Character exist in ARABIC_GLPHIES Matrix
			if(ArabicReshaper.ARABIC_GLPHIES[i][0]==target)
				return true;
		}

                for(int i = 0; i < ArabicReshaper.HARAKATE.length;i++){
			//Check if the target Character exist in ARABIC_GLPHIES Matrix
			if(ArabicReshaper.HARAKATE[i]==target)
				return true;
		}

		return false;
	}

	/**
	 * Helper function to split Sentence By Space
	 * @param sentence the Sentence to Split into Array of Words
	 * @return Array Of words
	 */
	private static String[] getWords(String sentence){
		if (sentence != null) {
			return sentence.split("\\s");
		} else {
			return new String[0];
		}
	}

	/**
	 * Helper function to check if the word has Arabic Letters
	 * @param word The to check Against
	 * @return true if the word has Arabic letters, false otherwise
	 */
	public static boolean hasArabicLetters(String word){

		//Iterate over the word to check all the word's letters
		for(int i=0;i<word.length();i++){

			if(isArabicCharacter(word.charAt(i)))
				return true;
		}
		return false;
	}

	/**
	 * Helper function to check if the word is all Arabic Word
	 * @param word The word to check against
	 * @return true if the word is Arabic Word, false otherwise
	 */
	public static boolean isArabicWord(String word){
		//Iterate over the Word
		for(int i=0;i<word.length();i++){
			if(!isArabicCharacter(word.charAt(i)))
				return false;
		}
		return true;
	}

	/**
	 * Helper function to split the Mixed Word into words with only Arabic, and English Words
	 * @param word The Mixed Word
	 * @return The Array of the Words of each Word may exist inside that word
	 */
	private static String[] getWordsFromMixedWord(String word){

		//The return result of words
		ArrayList finalWords=new ArrayList();

		//Temp word to hold the current word
		String tempWord="";

		//Iterate over the Word Length
		for(int i=0;i<word.length();i++){

			//Check if the Character is Arabic Character
			if(isArabicCharacter(word.charAt(i))){

				//Check if the tempWord is not empty, and what left in tempWord is not Arabic Word
				if(!tempWord.equals("") && !isArabicWord(tempWord)) {

					//add the Word into the Array
					finalWords.add(tempWord);

					//initiate the tempWord again
					tempWord=""+word.charAt(i);

				}else{

					//Not to add the tempWord, but to add the character to the rest of the characters
					tempWord+=word.charAt(i);
				}

			}else{

				//Check if the tempWord is not empty, and what left in tempWord is Arabic Word
				if(!tempWord.equals("") && isArabicWord(tempWord)){

					//add the Word into the Array
					finalWords.add(tempWord);

					//initiate the tempWord again
					tempWord=""+word.charAt(i);

				}else{

					//Not to add the tempWord, but to add the character to the rest of the characters
					tempWord+=word.charAt(i);
				}
			} 
		}

		String[] theWords=new String[finalWords.size()];
		theWords=(String[])finalWords.toArray(theWords);

		return theWords;
	}

	public static String reshape(String allText) {
		if (allText != null) {
			StringBuffer result = new StringBuffer();
			String[] sentences = allText.split("\n");
			for (int i = 0; i < sentences.length; i++) {
				result.append(reshapeSentence(sentences[i]));
				result.append("\n");
			}
			return result.toString();
		} else {
			return null;
		}
		
	}
	/**
	 * The Main Reshaping Function to be Used in Android Program
	 * @param allText The text to be Reshaped
	 * @return the Reshaped Text
	 */
	public static String reshapeSentence(String sentence){
		//get the Words from the Text
		String[] words=getWords(sentence);

		//prepare the Reshaped Text
		StringBuffer reshapedText=new StringBuffer("");

		//Iterate over the Words
		for(int i=0;i<words.length;i++){

			//Check if the Word has Arabic Letters
			if(hasArabicLetters(words[i])){

				//Check if the Whole word is Arabic
				if(isArabicWord(words[i])){

					//Initiate the ArabicReshaper functionality
					ArabicReshaper arabicReshaper = new ArabicReshaper(words[i]);
					

					//Append the Reshaped Arabic Word to the Reshaped Whole Text
					reshapedText.append(arabicReshaper.getReshapedWord());
				}else{ //The word has Arabic Letters, but its not an Arabic Word, its a mixed word

					//Extract words from the words (split Arabic, and English)
					String [] mixedWords=getWordsFromMixedWord(words[i]);

					//iterate over mixed Words
					for(int j=0;j<mixedWords.length;j++){

						//Initiate the ArabicReshaper functionality
						ArabicReshaper arabicReshaper=new ArabicReshaper(mixedWords[j]);

						//Append the Reshaped Arabic Word to the Reshaped Whole Text
						reshapedText.append(arabicReshaper.getReshapedWord());
					}
				}	
			}else{//The word doesn't have any Arabic Letters

				//Just append the word to the whole reshaped Text
				reshapedText.append(words[i]);
			}

			//Append the space to separate between words
			reshapedText.append(" ");
		}

		//return the final reshaped whole text
		return reshapedText.toString();
	}
	
	public static TextView getArabicEnabledTextView(Context context, TextView targetTextView) {
		//this is a static for testing!
		if (face == null) {
			face = Typeface.createFromAsset(context.getAssets(), FONTS_LOCATION_PATH);
		}
		targetTextView.setTypeface(face);
		targetTextView.setGravity(Gravity.RIGHT);
		return targetTextView;
	}
}

