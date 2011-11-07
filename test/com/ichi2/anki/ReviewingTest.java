package com.ichi2.anki;


import java.util.Random;

import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;

import com.jayway.android.robotium.solo.Solo;

/**
 * @author An Thanh Nguyen
 * 
 * This is a test class for AnkiDroid using Robotium.
 * It starts the StudyOptions activity, click on "Start Reviewing"
 * and then automatically answers all questions.
 *
 * To use this test:
 *   1. Load the deck you want to test in the emulator/device 
 *   2. Run this class as a Android JUnit Test
 *   
 *   
 */
public class ReviewingTest extends
		ActivityInstrumentationTestCase2<StudyOptions> {

	private static final int SHORT_SLEEP_TIME = 100;
	private static final String STUDY_OPTIONS_CLASS = "StudyOptions";
	private static final String REVIEWER_CLASS = "Reviewer";
	private Solo solo;
	private static int SLEEP_TIME = 250;// the time paused between actions.
	private Random rd;
	
	/**
	 * Constructor
	 */
	public ReviewingTest() {
		super("com.ichi2.anki", StudyOptions.class);
		// TODO Auto-generated constructor stub
	}


	public void setUp() throws Exception{
		solo = new Solo(getInstrumentation(), getActivity());
		rd = new Random();
	}
	
	/**
	 * @param pre prefix to add to the log
	 */
	private void logCurrentActivity(String pre){
		android.app.Activity activity = solo.getCurrentActivity();
		Log.d("Test Activity Title",  pre + ": " + (String) activity.getTitle() );
		Log.d("Test Activity Class",  pre + ": " + (String) activity.getLocalClassName() );
	}
	

	private void answerACard(){
		solo.sendKey(solo.DOWN);
		solo.sendKey(solo.ENTER);
		solo.sleep(SLEEP_TIME);
		
		if (finished())
			return;
		
		//Randomly select a answer
		//Assume that the current pointer is at the second button from the left
		int r = rd.nextInt(4);
		if (  r == 0)
			solo.sendKey(solo.LEFT);
		else 
			for (int i = 0; i < (r-1); i++)
				solo.sendKey(solo.RIGHT);
		
		solo.sendKey(solo.ENTER);
	}
	
	/**
	 * @return true if the current activity is not Reviewer
	 */
	private boolean finished(){
		
//		ArrayList<TextView> ArrayT = solo.getCurrentTextViews(null);
//		for ( TextView t : ArrayT ){
//			CharSequence chS = t.getText();
//			String s = chS.toString();
//			//Log.d("Test TextView", s);
//			String congrats = getActivity().getString(R.string.studyoptions_congrats_title);
//			if (s.equals(congrats) ) return true;
//		}
//		
//		return false;
		
		return (!solo.getCurrentActivity().getLocalClassName().equals(REVIEWER_CLASS));
	}
	
	/**
	 * Main test method
	 * Simulate the reviewing action
	 */
	public void testReviewingCards() {
		logCurrentActivity("Start");
		// Wait for the program to load
		while ( solo.getCurrentActivity().getTitle().toString().equals("AnkiDroid") )
			solo.sleep(SHORT_SLEEP_TIME);
		
		logCurrentActivity("After loaded");
		
		// Select "Start reviewing"
		solo.sendKey(solo.ENTER);
		solo.sleep(SLEEP_TIME);
		
		//Wait for the Reviewer to load
		while (solo.getCurrentActivity().getLocalClassName().equals(STUDY_OPTIONS_CLASS))
			solo.sleep(SHORT_SLEEP_TIME);
		
		logCurrentActivity("Before answer cards");
		
		while (true){
			if ( finished() ) break;
			answerACard();
			logCurrentActivity("Answered a card");
			solo.sleep(SLEEP_TIME);
		}
		
		// The test is passed if it can run to here
		assertTrue(true);
	}
	
	
	public void tearDown() throws Exception {
		try {
			solo.finalize();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
}
