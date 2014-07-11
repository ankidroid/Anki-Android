package com.wildplot.android.parsing.AtomTypes;

import com.wildplot.android.parsing.*;

import java.util.regex.Pattern;

/**
 * @author Michael Goldbach
 */
public class FunctionXAtom implements TreeElement {
    private Atom.AtomType atomType = Atom.AtomType.FUNCTION_X;
    private TopLevelParser parser;
    private Expression expression;
    private String funcName;

    public FunctionXAtom(String funcString, TopLevelParser parser){
        this.parser = parser;

        boolean isValid = init(funcString);
        if (!isValid){
            this.atomType = Atom.AtomType.INVALID;
        }
    }

    private boolean init(String funcString){
        int leftBracket = funcString.indexOf("(");
        int rightBracket = funcString.lastIndexOf(")");
        if(leftBracket > 1 && rightBracket > leftBracket+1){
            String funcName = funcString.substring(0, leftBracket);

            Pattern p = Pattern.compile("[^a-zA-Z0-9]");
            boolean hasSpecialChar = p.matcher(funcName).find();
            if(hasSpecialChar || !(funcName.length() > 0)){
                this.atomType = Atom.AtomType.INVALID;
                return false;
            }
            String expressionString = funcString.substring(leftBracket+1, rightBracket);
            Expression expressionInBrackets = new Expression(expressionString, parser);
            boolean isValidExpression = expressionInBrackets.getExpressionType() != Expression.ExpressionType.INVALID;
            if(isValidExpression){
                this.atomType = Atom.AtomType.FUNCTION_X;
                this.funcName = funcName;
                this.expression = expressionInBrackets;
                return true;
            }

        }

        return false;
    }

    @Override
    public double getValue() throws ExpressionFormatException{
        if (atomType != Atom.AtomType.INVALID){

            return parser.getFuncVal(funcName, expression.getValue());
        }
        else
            throw new ExpressionFormatException("Number is Invalid, cannot parse");
    }

    @Override
    public boolean isVariable() throws ExpressionFormatException{
        //TODO check how changed related function definitions are handled
        if (atomType != Atom.AtomType.INVALID){

            return expression.isVariable();
        }
        else
            throw new ExpressionFormatException("Number is Invalid, cannot parse");
    }

    public Atom.AtomType getAtomType() {
        return atomType;
    }
}
