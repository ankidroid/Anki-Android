package com.wildplot.android.parsing;



public class Factor implements TreeElement {
    private TopLevelParser parser;
    public static enum FactorType { PLUS_FACTOR, MINUS_FACTOR, POW, INVALID}
    private FactorType factorType = FactorType.INVALID;
    private Factor factor;
    private Pow pow;
    
    public Factor(String factorString, TopLevelParser parser){
        this.parser = parser;
        if(!TopLevelParser.stringHasValidBrackets(factorString)){
            this.factorType = FactorType.INVALID;
            return;
        }
        boolean isReady;

        isReady = initAsPlusFactor(factorString);
        if(!isReady)
            isReady = initAsMinusFactor(factorString);
        if(!isReady)
            isReady = initAsPow(factorString);
        if(!isReady)
            this.factorType = FactorType.INVALID;
    }

    private boolean initAsPlusFactor(String factorString){
        if(factorString.length() > 0 && factorString.charAt(0) == '+'){
            boolean isValidFactor;
            String leftSubString = factorString.substring(1, factorString.length());
            Factor leftFactor = new Factor(leftSubString, parser);
            isValidFactor = leftFactor.getFactorType() != FactorType.INVALID;
            if(isValidFactor){
                this.factorType = FactorType.PLUS_FACTOR;
                this.factor = leftFactor;
                return true;
            }
        }

        return false;
    }

    private boolean initAsMinusFactor(String factorString){
        if(factorString.length() > 0 && factorString.charAt(0) == '-'){
            boolean isValidFactor;
            String leftSubString = factorString.substring(1, factorString.length());
            Factor leftFactor = new Factor(leftSubString, parser);
            isValidFactor = leftFactor.getFactorType() != FactorType.INVALID;
            if(isValidFactor){
                this.factorType = FactorType.MINUS_FACTOR;
                this.factor = leftFactor;
                return true;
            }
        }

        return false;
    }

    private boolean initAsPow(String factorString){
        Pow pow = new Pow(factorString, parser);
        boolean isValidPow = pow.getPowType() != Pow.PowType.INVALID;
        if(isValidPow){
            this.factorType = FactorType.POW;
            this.pow = pow;
            return true;
        }
        return false;
    }

    public FactorType getFactorType() {
        return factorType;
    }

    public double getValue() throws ExpressionFormatException{
        switch (factorType) {
            case PLUS_FACTOR:
                return factor.getValue();
            case MINUS_FACTOR:
                return -factor.getValue();
            case POW:
                return pow.getValue();
            case INVALID:
            default:
                throw new ExpressionFormatException("cannot parse expression at factor level");
        }

    }

    @Override
    public boolean isVariable() throws ExpressionFormatException{
        switch (factorType) {
            case PLUS_FACTOR:
            case MINUS_FACTOR:
                return factor.isVariable();
            case POW:
                return pow.isVariable();
            case INVALID:
            default:
                throw new ExpressionFormatException("cannot parse expression at factor level");
        }
    }


}
