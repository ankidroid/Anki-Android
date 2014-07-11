package com.wildplot.android.parsing.AtomTypes;

import com.wildplot.android.parsing.Expression;
import com.wildplot.android.parsing.ExpressionFormatException;
import com.wildplot.android.parsing.TopLevelParser;
import com.wildplot.android.parsing.TreeElement;

/**
 * @author Michael Goldbach
 */
public class MathFunctionAtom implements TreeElement{
    private TopLevelParser parser;
    public static enum MathType {SIN, COS, TAN, SQRT, ACOS, ASIN, ATAN, SINH, COSH, LOG, LN, INVALID}
    private MathType mathType = MathType.INVALID;
    private Expression expression;
    private boolean hasSavedValue = false;
    private double savedValue = 0;

    public MathFunctionAtom(String funcString, TopLevelParser parser){
        this.parser = parser;
        boolean isValid = init(funcString);
        if(!isValid){
            this.mathType = MathType.INVALID;
        }
        if(isValid && !isVariable()){
            savedValue = getValue();
            hasSavedValue = true;
        }
    }



    private boolean init(String funcString){
        int leftBracket = funcString.indexOf("(");
        int rightBracket = funcString.lastIndexOf(")");
        if(leftBracket > 1 && rightBracket > leftBracket+1){
            String funcName = funcString.substring(0, leftBracket);
            String expressionString = funcString.substring(leftBracket+1, rightBracket);
            Expression expressionInBrackets = new Expression(expressionString, parser);
            boolean isValidExpression = expressionInBrackets.getExpressionType() != Expression.ExpressionType.INVALID;
            if(isValidExpression){
                if(funcName.equals("sin")){
                    this.mathType = MathType.SIN;
                }else if (funcName.equals("cos")){
                    this.mathType = MathType.COS;
                }else if (funcName.equals("tan")){
                    this.mathType = MathType.TAN;
                }else if (funcName.equals("sqrt")){
                    this.mathType = MathType.SQRT;
                }else if (funcName.equals("acos")){
                    this.mathType = MathType.ACOS;
                }else if (funcName.equals("asin")){
                    this.mathType = MathType.ASIN;
                }else if (funcName.equals("atan")){
                    this.mathType = MathType.ATAN;
                }else if (funcName.equals("sinh")){
                    this.mathType = MathType.SINH;
                }else if (funcName.equals("cosh")){
                    this.mathType = MathType.COSH;
                }else if (funcName.equals("log") || funcName.equals("lg")){
                    this.mathType = MathType.LOG;
                }else if (funcName.equals("ln")){
                    this.mathType = MathType.LN;
                }else {
                    this.mathType = MathType.INVALID;
                    return false;
                }
                this.expression = expressionInBrackets;
                return true;




            }

        }

        return false;
    }

    @Override
    public double getValue() throws ExpressionFormatException{
        if(hasSavedValue)
            return savedValue;

        switch (mathType) {
            case SIN:
                return Math.sin(expression.getValue());
            case COS:
                return Math.cos(expression.getValue());
            case TAN:
                return Math.tan(expression.getValue());
            case SQRT:
                return Math.sqrt(expression.getValue());
            case ACOS:
                return Math.acos(expression.getValue());
            case ASIN:
                return Math.asin(expression.getValue());
            case ATAN:
                return Math.atan(expression.getValue());
            case SINH:
                return Math.sinh(expression.getValue());
            case COSH:
                return Math.cosh(expression.getValue());
            case LOG:
                return Math.log10(expression.getValue());
            case LN:
                return Math.log(expression.getValue());
            case INVALID:
            default:
                throw new ExpressionFormatException("Number is Invalid, cannot parse");
        }
    }

    @Override
    public boolean isVariable() {
        if (mathType != MathType.INVALID){

            return expression.isVariable();
        }
        else
            throw new ExpressionFormatException("Number is Invalid, cannot parse");
    }

    public MathType getMathType() {
        return mathType;
    }
}
