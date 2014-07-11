package com.wildplot.android.parsing;



public class Term implements TreeElement{
    private TopLevelParser parser;
    public static enum TermType { TERM_MUL_FACTOR, TERM_DIV_FACTOR, FACTOR, INVALID};
    private TermType termType = TermType.INVALID;
    private Factor factor = null;
    private Term term = null;
    
    
    public Term(String termString, TopLevelParser parser){
        this.parser = parser;

        if(!TopLevelParser.stringHasValidBrackets(termString)){
            this.termType = TermType.INVALID;
            return;
        }

        boolean isReady = false;

        isReady = initAsTermMulOrDivFactor(termString);
        if(!isReady)
            isReady = initAsFactor(termString);
        if(!isReady)
            this.termType = TermType.INVALID;
    }
    
    private boolean initAsTermMulOrDivFactor(String termString){
        int bracketChecker = 0;
        for(int i = 0; i< termString.length(); i++){
            if(termString.charAt(i) == '('){
                bracketChecker++;
            }
            if(termString.charAt(i) == ')'){
                bracketChecker--;
            }
            if((termString.charAt(i) == '*' || termString.charAt(i) == '/') && bracketChecker == 0){
                String leftSubString = termString.substring(0, i);
                if(!TopLevelParser.stringHasValidBrackets(leftSubString))
                    continue;
                Term leftTerm = new Term(leftSubString, parser);
                boolean isValidFirstPartTerm = leftTerm.getTermType() != TermType.INVALID;
                
                if(!isValidFirstPartTerm)
                    continue;
                
                boolean isValidSecondPartFactor = false;
                String rightSubString = termString.substring(i+1, termString.length());
                if(!TopLevelParser.stringHasValidBrackets(rightSubString))
                    continue;
                Factor rightFactor = new Factor(rightSubString, parser);
                isValidSecondPartFactor = rightFactor.getFactorType() != Factor.FactorType.INVALID;
                
                if(isValidSecondPartFactor){
                    if(termString.charAt(i) == '*')
                        this.termType = TermType.TERM_MUL_FACTOR;
                    else
                        this.termType = TermType.TERM_DIV_FACTOR;
                    this.term=leftTerm;
                    this.factor=rightFactor;
                    return true;
                }
                
            }
        }
        
        return false;
    }

    private boolean initAsFactor(String termString){
        Factor factor = new Factor(termString, parser);
        boolean isValidTerm = factor.getFactorType() != Factor.FactorType.INVALID;
        if(isValidTerm){
            this.termType = TermType.FACTOR;
            this.factor = factor;
            return true;
        }
        return false;
    }
    
    
    public TermType getTermType() {
        return termType;
    }
    public double getValue() throws ExpressionFormatException{
        switch (termType) {
            case TERM_MUL_FACTOR:
                return term.getValue() * factor.getValue();
            case TERM_DIV_FACTOR:
                return  term.getValue() / factor.getValue();
            case FACTOR:
                return factor.getValue();
            case INVALID:
            default:
                throw new ExpressionFormatException("could not parse Term");
        }
    }

    @Override
    public boolean isVariable() {
        switch (termType) {
            case TERM_MUL_FACTOR:
            case TERM_DIV_FACTOR:
                return  term.isVariable() || factor.isVariable();
            case FACTOR:
                return factor.isVariable();
            case INVALID:
            default:
                throw new ExpressionFormatException("could not parse Term");
        }
    }


}
