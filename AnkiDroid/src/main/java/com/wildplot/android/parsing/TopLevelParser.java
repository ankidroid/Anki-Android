/****************************************************************************************
 * Copyright (c) 2014 Michael Goldbach <michael@wildplot.com>                           *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.wildplot.android.parsing;

import com.wildplot.android.rendering.interfaces.Function2D;
import com.wildplot.android.rendering.interfaces.Function3D;

import java.util.HashMap;
import java.util.Random;
import java.util.regex.Pattern;

public class TopLevelParser implements Function2D, Function3D, Cloneable{
    Random random = new Random();
    private HashMap<String, TopLevelParser> parserRegister;
    private HashMap<String, Double> varMap = new HashMap<>();
    private double x = 0.0, y = 0.0;
    private Expression expression = null;
    private boolean isValid = false;
    private String expressionString;
    private String xName = "x", yName = "y";
    private String funcName = "f" +random.nextInt();


    public TopLevelParser(String expressionString, HashMap<String, TopLevelParser> parserRegister){
        initVarMap();
        this.parserRegister = parserRegister;
        this.expressionString = expressionString;
        boolean isValidExpressionString = initExpressionString();

        this.expression =  new Expression(this.expressionString, this);
        this.isValid = (expression.getExpressionType() != Expression.ExpressionType.INVALID) && isValidExpressionString;

    }

    private void initVarMap(){
        varMap.put("e", Math.E);
        varMap.put("pi", Math.PI);
    }

    private boolean initExpressionString(){
        this.expressionString = expressionString.replace(" ", "");
        int equalPosition = expressionString.indexOf("=");
        if(equalPosition >= 1){
            String leftStatement = expressionString.substring(0, equalPosition);
            this.expressionString = expressionString.substring(equalPosition+1, expressionString.length());
            int commaPos = leftStatement.indexOf(",");
            int leftBracketPos = leftStatement.indexOf("(");
            int rightBracketPos = leftStatement.indexOf(")");

            if(leftBracketPos > 0 && rightBracketPos > leftBracketPos +1){
                String funcName = leftStatement.substring(0, leftBracketPos);
                Pattern p = Pattern.compile("[^a-zA-Z0-9]");
                boolean hasSpecialChar = p.matcher(funcName).find();
                if(hasSpecialChar){
                    return false;
                }
                if(commaPos == -1){
                    String xVarName = leftStatement.substring(leftBracketPos+1, rightBracketPos);
                    hasSpecialChar = p.matcher(xVarName).find();
                    if(hasSpecialChar){
                       return false;
                    }
                    this.xName = xVarName;
                } else {
                    String xVarName = leftStatement.substring(leftBracketPos+1, commaPos);
                    hasSpecialChar = p.matcher(xVarName).find();
                    if(hasSpecialChar){
                        return false;
                    }
                    String yVarName = leftStatement.substring(commaPos+1, rightBracketPos);
                    hasSpecialChar = p.matcher(yVarName).find();
                    if(hasSpecialChar){
                        return false;
                    }


                    this.xName = xVarName;
                    this.yName = yVarName;
                }
                this.funcName = funcName;
            }else {
                return false;
            }



        }

        return true;
    }

    public double getVarVal(String varName){
        return varMap.get(varName);
    }
    public void setVarVal(String varName, String varVal){
        varMap.put(varName, Double.parseDouble(varVal));
    }
    public void setVarVal(String varName, double varVal){
        varMap.put(varName, varVal);
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    @Override
    public double f(double x) {
        this.x = x;
        if(isValid)
            return expression.getValue();
        else
            throw new ExpressionFormatException("illegal Expression, cannot parse and return value");
    }

    public boolean isValid() {
        return isValid;
    }

    @Override
    public double f(double x, double y) {
        this.x = x;
        this.y = y;
        if(isValid)
            return expression.getValue();
        else
            throw new ExpressionFormatException("illegal Expression, cannot parse and return value");
    }


    public TopLevelParser createCopy(){
		HashMap<String, TopLevelParser> newParserRegister = new HashMap<>();
		for(String key : parserRegister.keySet()){
			newParserRegister.put(key, parserRegister.get(key).createCopy(newParserRegister));
		}
		
        return newParserRegister.get(this.funcName);
    }

    public TopLevelParser createCopy(HashMap<String, TopLevelParser> newParserRegister){
        return new TopLevelParser(this.expressionString, newParserRegister);
    }

    public double getFuncVal(String funcName, double xVal){
        TopLevelParser funcParser = this.parserRegister.get(funcName);
        return funcParser.f(xVal);
    }

    public double getFuncVal(String funcName, double xVal, double yVal){
        TopLevelParser funcParser = this.parserRegister.get(funcName);
        return funcParser.f(xVal, yVal);
    }

    public String getxName() {
        return xName;
    }

    public String getyName() {
        return yName;
    }

    public String getFuncName() {
        return funcName;
    }

    public static boolean stringHasValidBrackets(String string){
        int finalBracketCheck = string.replaceAll("\\(", "").length()- string.replaceAll("\\)", "").length();
        if(finalBracketCheck != 0)
            return false;

        int bracketOpeningCheck = 0;
        for(int i = 0; i<string.length(); i++){
            if(string.charAt(i) == '('){
                bracketOpeningCheck++;
            }
            if(string.charAt(i) == ')'){
                bracketOpeningCheck--;
            }
            if(bracketOpeningCheck <0){
                return false;
            }
        }
        return true;
    }
}
