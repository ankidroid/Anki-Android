package com.wildplot.android.parsing;

/**
 * Created by mig on 25.05.13.
 */
public class Pow implements TreeElement{
    private TopLevelParser parser;
    public static enum PowType {ATOM, ATOM_POW_FACTOR, ATOM_SQRT_FACTOR, INVALID};
    private PowType powType = PowType.INVALID;
    private Atom atom;
    private Factor factor;

    public Pow(String powString, TopLevelParser parser){
        this.parser = parser;
        if(!TopLevelParser.stringHasValidBrackets(powString)){
            this.powType = PowType.INVALID;
            return;
        }
        boolean isReady;

        isReady = initAsAtom(powString);
        if(!isReady)
            isReady = initAsAtomPowFactor(powString);
        if(!isReady)
            isReady = initAsAtomSqrtFactor(powString);
        if(!isReady)
            this.powType = PowType.INVALID;
    }

    private boolean initAsAtom(String powString){
        Atom atom = new Atom(powString, parser);
        boolean isValidAtom = atom.getAtomType() != Atom.AtomType.INVALID;
        if(isValidAtom){
            this.powType = PowType.ATOM;
            this.atom = atom;
            return true;
        }
        return false;
    }
    private boolean initAsAtomPowFactor(String powString){
        int opPos = powString.indexOf("^");
        if(opPos > 0){
            String leftAtomString = powString.substring(0,opPos);
            String rightFactorString = powString.substring(opPos+1, powString.length());
            if(!TopLevelParser.stringHasValidBrackets(leftAtomString) || !TopLevelParser.stringHasValidBrackets(rightFactorString))
                return false;
            Atom leftAtom = new Atom(leftAtomString, parser);
            boolean isValidAtom = leftAtom.getAtomType() != Atom.AtomType.INVALID;
            if(isValidAtom){
                Factor rightFactor = new Factor(rightFactorString, parser);
                boolean isValidFactor = rightFactor.getFactorType() != Factor.FactorType.INVALID;
                if(isValidFactor){
                    this.powType= PowType.ATOM_POW_FACTOR;
                    this.atom = leftAtom;
                    this.factor = rightFactor;
                    return true;
                }
            }
        }

        return false;
    }

    private boolean initAsAtomSqrtFactor(String powString){
        int opPos = powString.indexOf("**");
        if(opPos > 0){
            String leftAtomString = powString.substring(0,opPos);
            String rightFactorString = powString.substring(opPos+2, powString.length());
            if(!TopLevelParser.stringHasValidBrackets(leftAtomString) || !TopLevelParser.stringHasValidBrackets(rightFactorString))
                return false;
            Atom leftAtom = new Atom(leftAtomString, parser);
            boolean isValidAtom = leftAtom.getAtomType() != Atom.AtomType.INVALID;
            if(isValidAtom){
                Factor rightFactor = new Factor(rightFactorString, parser);
                boolean isValidFactor = rightFactor.getFactorType() != Factor.FactorType.INVALID;
                if(isValidFactor){
                    this.powType= PowType.ATOM_SQRT_FACTOR;
                    this.atom = leftAtom;
                    this.factor = rightFactor;
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public double getValue() throws ExpressionFormatException{
        switch (powType) {
            case ATOM:
                return atom.getValue();
            case ATOM_POW_FACTOR:
                return Math.pow(atom.getValue(), factor.getValue());
            case ATOM_SQRT_FACTOR:
                return Math.pow(atom.getValue(), 1.0/factor.getValue());
            case INVALID:
            default:
                throw new ExpressionFormatException("cannot parse Atom expression");
        }
    }

    @Override
    public boolean isVariable() throws ExpressionFormatException{
        switch (powType) {
            case ATOM:
                return atom.isVariable();
            case ATOM_POW_FACTOR:
            case ATOM_SQRT_FACTOR:
                return atom.isVariable() || factor.isVariable();
            case INVALID:
            default:
                throw new ExpressionFormatException("cannot parse Atom expression");
        }
    }

    public PowType getPowType() {
        return powType;
    }
}
