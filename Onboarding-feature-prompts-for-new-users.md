This documentation explains how to add a new feature prompt on any screen of the AnkiDroid app.

## Library which is being used

https://github.com/sjwall/MaterialTapTargetPrompt

## Way to add a new feature prompt

Visit the `Onboarding.kt` file.

Suppose a tutorial needs to be added for an activity called MyActivity. Steps for doing it:
- If an inner class for MyActivity exists, then use it otherwise create a new class inside Onboarding and initialise an object of that class inside MyActivity. In this case, call onCreate() if the tutorial needs to be displayed when the screen is opened and does not have any particular time or view visibility on which it depends. If the class already exists, go to step 3.
- If MyActivity does not already exist, then create it by extending Onboarding and then create a new enum class implementing OnboardingFlag.
- Create a new method to display the tutorial.
- Add the function using TutorialArgument data class to the list of tutorials in the init block if it has to be invoked which the screen is opened. If the function has to be invoked at a particular time, then call it from MyActivity.
- For any extra condition that needs to be checked before displaying a tutorial, add it as the 'mOnboardingCondition' parameter in TutorialArguments.

## CustomMaterialTapTargetPromptBuilder

CustomMaterialTapTargetPromptBuilder inherits from `MaterialTapTargetPrompt.Builder` and takes two parameters in its constructor: `activity` which represents the context of the activity and `featureIdentifier` which is an enum entry to identify the particular feature and set it as visited when the `show` method of CustomMaterialTapTargetPromptBuilder is invoked.

To customize the shape of a feature prompt, use one of the methods out of `createRectangle`, `createRectangleWithDimmedBackground`, `createCircle` and `createCircleWithDimmedBackground`

`setFocalColourResource` can be used to customize the colour of a feature prompt.

`setDismissedListener` takes a function as parameter and invokes it when the particular feature prompt on which it is called is dismissed.

`show` method is responsible for setting the feature as visited and showing the feature prompt. It also makes the focal colour as transparent for night mode so that the contents being highlighted are visible properly. Also, `captureTouchEventOutsidePrompt` is being set to `true` to prevent click on any outside view when user tries to dismiss a feature prompt. 