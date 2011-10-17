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
import java.util.regex.Pattern;

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

	// heuristic trying to determine cases where Android's WebView will forget
	// to display the arabic words right-to-left:
	// - harakates (taken from ArabicReshaper for now, to be validated...)
	// - isolated alif (\u0627)
	// - others?
    // TODO factoriser
    private static final Pattern patternBreakingWebView = Pattern.compile(
    	"[" +
		"\\u0627" +
    	"\\u0600\\u0601\\u0602\\u0603\\u0606\\u0607\\u0608\\u0609\\u060A\\u060B\\u060D\\u060E" +	
		"\\u0610\\u0611\\u0612\\u0613\\u0614\\u0615\\u0616\\u0617\\u0618\\u0619\\u061A\\u061B\\u061E\\u061F" +
		"\\u0621" +		
		"\\u063B\\u063C\\u063D\\u063E\\u063F" +		
		"\\u0640\\u064B\\u064C\\u064D\\u064E\\u064F" +		
		"\\u0650\\u0651\\u0652\\u0653\\u0654\\u0655\\u0656\\u0657\\u0658\\u0659\\u065A\\u065B\\u065C\\u065D\\u065E" +      
		"\\u0660\\u066A\\u066B\\u066C\\u066F\\u0670\\u0672'" +
		"\\u06D4\\u06D5\\u06D6\\u06D7\\u06D8\\u06D9\\u06DA\\u06DB\\u06DC\\u06DF" +
		"\\u06E0\\u06E1\\u06E2\\u06E3\\u06E4\\u06E5\\u06E6\\u06E7\\u06E8\\u06E9\\u06EA\\u06EB\\u06EC\\u06ED\\u06EE\\u06EF" +
		"\\u06D6\\u06D7\\u06D8\\u06D9\\u06DA\\u06DB\\u06DC\\u06DD\\u06DE\\u06DF" +
		"\\u06F0\\u06FD" +
		"\\uFE70\\uFE71\\uFE72\\uFE73\\uFE74\\uFE75\\uFE76\\uFE77\\uFE78\\uFE79\\uFE7A\\uFE7B\\uFE7C\\uFE7D\\uFE7E\\uFE7F" +
		"\\uFC5E\\uFC5F\\uFC60\\uFC61\\uFC62\\uFC63" +
		"]"
    		);


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
		ArrayList<String> finalWords=new ArrayList<String>();

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
		
		//add the final Word
		if(!"".equals(tempWord)){
			finalWords.add(tempWord);
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
	 * @param forWebView whether the word will be displayed in a WebView
	 * @return the Reshaped Text
	 */
	public static String reshapeSentence(String sentence, boolean forWebView){
		//get the Words from the Text
		String[] words=getWords(sentence);

		//prepare the Reshaped Text
		StringBuffer reshapedText=new StringBuffer("");
		//used to buffer arabic text until we encountered non-arabic
		StringBuffer arabicText = new StringBuffer("");
		//Iterate over the Words
		for(int i=0;i<words.length;i++){

			//Check if the Word has Arabic Letters
			if(hasArabicLetters(words[i])){

				//Check if the Whole word is Arabic
				if(isArabicWord(words[i])){

					//Initiate the ArabicReshaper functionality
					ArabicReshaper arabicReshaper = new ArabicReshaper(words[i]);
					
					//Append the Reshaped Arabic Word to arabic buffer
					arabicText.append(arabicReshaper.getReshapedWord());
				}else{ //The word has Arabic Letters, but its not an Arabic Word, its a mixed word

					//Extract words from the words (split Arabic, and English)
					String [] mixedWords=getWordsFromMixedWord(words[i]);

					//iterate over mixed Words
					for(int j=0;j<mixedWords.length;j++){
						
						if(isArabicWord(mixedWords[j])){
							
							//Initiate the ArabicReshaper functionality
							ArabicReshaper arabicReshaper=new ArabicReshaper(mixedWords[j]);

							//Append the Reshaped Arabic Word to the Arabic buffer
							arabicText.append(arabicReshaper.getReshapedWord());
						}else{
							
							// append the buffered arabic text
							reshapedText.append(arabicText);
							arabicText = new StringBuffer("");
							// append the word to the whole reshaped Text
							reshapedText.append(mixedWords[j]);
						}
					}
				}	
			}else{//The word doesn't have any Arabic Letters

				// append the buffered arabic text
				reshapedText.append(arabicText);
				arabicText = new StringBuffer("");
				// append the word to the whole reshaped Text
				reshapedText.append(words[i]);
			}
			
			//Append the space to separate between words
			//to the arabic buffer if the previous word was arabic, to the reshaped text otherwise
			if(arabicText.length() > 0){

				arabicText.append(" ");
			}else{
				
				reshapedText.append(" ");
			}
		}
		
		// append the final arabic sequence
		reshapedText.append(arabicText);

		//return the final reshaped whole text
		return manualRTL(reshapedText, forWebView).toString();
	}
	
	public static String reshapeSentence(String sentence) {
		return reshapeSentence(sentence, false);
	}
	
	public static StringBuffer manualRTL(StringBuffer text, boolean forWebView){

		if(forWebView){
			if(patternBreakingWebView.matcher(text).find()) {
				// handle BiDi manually
				// global direction is LTR because of HTML markup
				StringBuffer ltr = new StringBuffer(""), rtl = new StringBuffer("");
				int i = 0;
				while(i < text.length())
				{
					char c  = text.charAt(i);
					if(isStrongLeftToRight(c) || !isStrongRightToLeft(c))
					{
						while(i < text.length() && !isStrongRightToLeft(text.charAt(i)))
						{
							ltr.append(text.charAt(i));
							i++;
						}
						if(i == text.length())
						{
							break;
						}
					}
					if(isStrongRightToLeft(text.charAt(i)))
					{
						while(i < text.length() && !isStrongLeftToRight(text.charAt(i)))
						{
							// additional end condition: start of an HTML tag
							if(text.charAt(i) == '<')
							{
								break;
							}
							rtl.append(text.charAt(i));
							i++;
						}
						// reverse only words that Android will not display correctly by itself
						if(patternBreakingWebView.matcher(rtl).find())
						{
							ltr.append(rtl.reverse());
						}
						else
						{
							ltr.append(rtl);
						}
						rtl = new StringBuffer("");
					}
				}
				return ltr;
			}
		}
		return text;
	}
	
	public static boolean isStrongLeftToRight(char c)
	{
		byte dir = Character.getDirectionality(c);
		return (dir == Character.DIRECTIONALITY_LEFT_TO_RIGHT) ||
				(dir == Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING) ||
				(dir == Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE);
	}
	

	public static boolean isStrongRightToLeft(char c)
	{
		byte dir = Character.getDirectionality(c);
		return (dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT) ||
				(dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) ||
				(dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING) ||
				(dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE);
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

