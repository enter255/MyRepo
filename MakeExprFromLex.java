package udbparser.expr;


import syntax.expression.*;
import syntax.statement.Function;
import udbparser.usingperl.LexemeNode;

import java.util.*;

public class MakeExprFromLex {
    /********
     * Field*/
    private ArrayList<ArrayList<LexemeNode>> lxm_;
    private LinkedList<LinkedList<Expression>> setOfExpression;
    private ArrayList<Function> functionInformations;
    private String thisFunctionsName;
    private ArrayList<IdExpression> variables;
    private ArrayList<String> binaryOperator;
    private ArrayList<String> singleOperator;
    private HashMap<String,Integer> totalOperators;


    public LinkedList<LinkedList<Expression>> getSetOfExpression() {
        return setOfExpression;
    }

    /****************
     * Main Function*/
    public MakeExprFromLex (ArrayList<Function> functions, String funtionName, ArrayList<ArrayList<LexemeNode>> lxm){
        lxm_ = lxm; //total lexemes
        functionInformations = functions; //total function informations
        thisFunctionsName = funtionName; //this function's name
        setOfExpression = new LinkedList<>(); //result expressions
        //informations
        InfoForExpressions infoForExpressions = new InfoForExpressions(lxm);
        variables = infoForExpressions.getVariables(); //variable informations
        binaryOperator = infoForExpressions.getBinaryOperator();
        singleOperator = infoForExpressions.getSingleOperator();
        totalOperators = infoForExpressions.getTotalOperators();

/*
        //detect function parameter's variables
        for(Function func : functions){
            if(func.getName().equals(thisFunctionsName)) {
                for(IdExpression var : func.getFormalParameter())
                    variables.add(var);
                break;
            }
        }
*/

//        System.out.println("**** " + funtionName +" ****");
        //make expression list from total lexeme
        for(ArrayList<LexemeNode> lex : lxm){
            try {
                LinkedList<Expression> expressions = expression(lex);
                /*for(Expression expr : expressions) {
                    if (expr instanceof NullExpression)
                        continue;
                    System.out.println(expr.getRawData('0'));
                }*/
                setOfExpression.add(expressions);
            }catch (NullPointerException e){
                System.out.println("~~~~~~~~~ERROR : MakeExprFromLex.java >> MakeExprFromLex()~~~~~~~~~");
            }
        }
    }

    /** return result of expression list from lexeme list **/
    LinkedList<Expression> expression(ArrayList<LexemeNode> lexemeList) {
        int currentIndex=0;
        ArrayList<LexemeNode> lexeme;
        LinkedList<Expression> expressionList = new LinkedList<>();

        while (currentIndex < lexemeList.size()) {
            //lexeme = lexemeList[currentIndex ~ before " , ; "]
            lexeme = getLexUntilToken(lexemeList, currentIndex);
            //print original splitted lexeme
            for(LexemeNode l : lexeme)
                System.out.print(l.getData());
            System.out.println("\n-------------------------");
            expressionList.addAll(getExprFromSplitedLex(lexeme));
            currentIndex += lexeme.size() + 1;
        }

        if(expressionList.size()==0)
            expressionList.add(new NullExpression());
        return  expressionList;
    }

    /** split lexeme list by token ( , ; ) **/
    ArrayList<LexemeNode> getLexUntilToken(ArrayList<LexemeNode> lexeme, int startIndex) {
        ArrayList<LexemeNode> splitedLexeme = new ArrayList<>();
        int curIndex = 0;
        int count = 0;

        for(LexemeNode lex : lexeme) {
            if(curIndex < startIndex) {
                curIndex++; continue;
            }
            if(lex.getData().equals("(") || lex.getData().equals("["))
                count++;
            if(lex.getData().equals(")") || lex.getData().equals("]"))
                count--;
            if((lex.getData().equals(",") || lex.getData().equals(";")) && count == 0) {
                break;
            }
            //splitedLexeme = lexeme[ startIndex ~ before "token" ]
            splitedLexeme.add(lex);
        }
        return splitedLexeme;
    }

    /** make one expression from lexeme list **/
    ArrayList<Expression> getExprFromSplitedLex(ArrayList<LexemeNode> lexeme) {
        ArrayList<Expression> resExpression = new ArrayList<>();
        Stack<LxmExprPair> stack = new Stack<>();

        if(lexeme.size()==1){
            LexemeNode lex = lexeme.get(0);
            if((lex.getData().equals("break"))
                    || (lex.getData().equals("continue"))
                    || (lex.getData().equals("NULL") && lex.getRef_kind().equals("NULL") && lex.getToken().equals("NULL"))) {
                resExpression.add(new NullExpression());
                return resExpression;
            }
        }

        //1st. convert lexeme list into pair of <LexemeNode,Expression>
        stack = expressInsideOfBracket(lexeme, stack); //()
        System.out.println(">>> expressInsideOfBracket <<<");
        printPhase(stack);
        //2nd. make call expression
        stack = expressCall(stack); //()
        System.out.println(">>> expressCall <<<");
        printPhase(stack);
        //3th. make array expression
        stack = expressArray(stack); //[]
        System.out.println(">>> expressArray <<<");
        printPhase(stack);
        //4th. determine whether its binary or unary
        stack = classifyBinaryUnary(stack);
        System.out.println(">>> classifyBinaryUnary <<<");
        printPhase(stack);
        //5th. express some operators
        stack = expressSomeOperators(stack);
        System.out.println(">>> expressSomeOperators <<<");
        printPhase(stack);
        //6th. make cast expression
        stack = expressCast(stack); //(type)
        System.out.println(">>> expressCast <<<");
        printPhase(stack);
        //7rd. make identifier expression & literal expression
        stack = expressIdLiteral(stack);
        System.out.println(">>> expressIdLiteral <<<");
        printPhase(stack);
        //8th. make binary, unary, conditional expression
        stack = expressBinaryUnaryConditional(stack); //other operators
        System.out.println(">>> expressBinaryUnaryConditional <<<");
        printPhase(stack);
        //9th. fill up declaration expression
        stack = expressDecl(stack);
        System.out.println(">>> expressDecl <<<");
        printPhase(stack);
        System.out.println("-------------------\n");

        if(stack.isEmpty()) {
            resExpression.add(new NullExpression());
            return resExpression;
        }
        //the result will be the one expression
        try{
            while(stack.isEmpty()==false)
                resExpression.add(stack.pop().expression);
        }catch(EmptyStackException e){
            resExpression.add(new NullExpression());
            System.out.println("\nERROR : MakeExprFromLex.java >> getExprFromSplitedLex()\n");
        }
        return  resExpression;
    }


    /***************
     * Sub Function*/
    /** make expression list from lexeme which are in bracket **/
    Stack<LxmExprPair> expressInsideOfBracket(ArrayList<LexemeNode> lexeme, Stack<LxmExprPair> resStack) {
        ArrayList<LexemeNode> lexemeOfBracket;
        LinkedList<Expression> exprOfBracket;
        int currentIndex = 0, startindex = 0;

        for(LexemeNode lex : lexeme) {
            if(currentIndex < startindex) {
                currentIndex++; continue;
            }
            //System.out.println(lex.toString());
            //just push all lexeme include "( ) [ ]"
            if(!lex.getToken().equals("Whitespace") && !lex.getToken().equals("Newline"))
                resStack.push(new LxmExprPair(lex, null));
            //when meet bracket, push converted expression which are in bracket
            if(lex.getData().equals("(") || lex.getData().equals("[")) {
                lexemeOfBracket = getLexUntilEndBracket(lexeme,currentIndex);
                startindex += lexemeOfBracket.size();
                /* ex. (int)(number) */
                if(lexemeOfBracket.size() == 1 && lexemeOfBracket.get(0).getToken().equals("Keyword"))
                    resStack.push(new LxmExprPair(lexemeOfBracket.get(0), null));
                /* ex. (struct s)(ary[top]) */
                else if((lexemeOfBracket.size() == 2 && lexemeOfBracket.get(0).getToken().equals("Keyword")
                        && (lexemeOfBracket.get(1).getRef_kind().equals("Type") || lexemeOfBracket.get(1).getToken().equals("Keyword")))) {
                    lex = new LexemeNode();
                    lex.setData(lexemeOfBracket.get(0).getData() + " " + lexemeOfBracket.get(1).getData());
                    lex.setRef_kind("NULL");
                    lex.setToken("Keyword");
                    resStack.push(new LxmExprPair(lex, null));
                }
                else if(lexemeOfBracket.size()==3 && lexemeOfBracket.get(1).getToken().equals("Whitespace")
                        && lexemeOfBracket.get(0).getToken().equals("Keyword")
                        && (lexemeOfBracket.get(2).getToken().equals("Keyword") || lexemeOfBracket.get(2).getRef_kind().equals("Type"))) {
                    lex = new LexemeNode();
                    lex.setData(lexemeOfBracket.get(0).getData() + " " + lexemeOfBracket.get(2).getData());
                    lex.setRef_kind("NULL");
                    lex.setToken("Keyword");
                    resStack.push(new LxmExprPair(lex, null));
                }
                /* ex. func(10, a+b, c*5) */
                else {
                    exprOfBracket = expression(lexemeOfBracket);
                    //push expression list which converted from bracket lexeme list
                    for(Expression expr : exprOfBracket)
                        resStack.push(new LxmExprPair(null, expr));
                }
            }
            currentIndex++; startindex++;
        }
        return  resStack;
    }

    /** make call expression */
    Stack<LxmExprPair> expressCall(Stack<LxmExprPair> stack) {
        Stack<LxmExprPair> resStack = new Stack<>();
        ArrayList<Expression> parameters = new ArrayList<>();
        int currentIndex = 0, startIndex = 0;

        for(LxmExprPair pair : stack) {
            if(currentIndex < startIndex) {
                currentIndex++; continue;
            }
            //ex. func(expr1, expr2, ...)
            if(pair.expression == null && (pair.lexemeNode.getRef_kind().equals("Call") || pair.lexemeNode.getData().equals("sizeof"))) {
                //get parameters
                if(stack.get(startIndex+1).lexemeNode!=null && stack.get(startIndex+1).lexemeNode.getData().equals("(")) {
                    startIndex+=2;
                    while(stack.get(startIndex).expression != null) {
                        parameters.add(stack.get(startIndex).expression);
                        startIndex++;
                    }
                    //now index is on ")"
                }
                //when its library function  //ex. printf()
                Function function = searchFunction(pair.lexemeNode.getData());
                if(function.getName().equals("NULL")) {
                    LibraryCallExpression libCallExpr = new LibraryCallExpression();
                    libCallExpr.setFunctionalName(pair.lexemeNode.getData());
                    libCallExpr.setActualParameters(parameters);
                    resStack.push(new LxmExprPair(null,libCallExpr));
                }
                //when its user defined function
                else {
                    UserDefinedCallExpression userCallExpr = new UserDefinedCallExpression();
                    userCallExpr.setCalleeFunction(function);
                    userCallExpr.setActualParameters(parameters);
                    resStack.push(new LxmExprPair(null,userCallExpr));
                }
            }
            //just others
            else {
                resStack.push(pair);
                currentIndex++; startIndex++;
            }
        }
        return resStack;
    }

    /** make array expression **/
    Stack<LxmExprPair> expressArray(Stack<LxmExprPair> stack) {
        Stack<LxmExprPair> resStack = new Stack<>();
        ArrayExpression aryExpr;
        int currentindex = 0, startIndex = 0;

        //in here, now we have only "[ ]"
        for(LxmExprPair pair : stack) {
            if(currentindex < startIndex) {
                currentindex++; continue;
            }
            if(pair.lexemeNode != null && pair.lexemeNode.getData().equals("[")) {
                aryExpr = new ArrayExpression();
                LxmExprPair pre_pair = resStack.pop();
                //ex. ary[i+j] : lexemeNode + [ + binExpression + ]
                if(pre_pair.lexemeNode != null && pre_pair.lexemeNode.getToken().equals("Identifier")) {
                    IdExpression idExpr = searchVariable(pre_pair.lexemeNode.getData());
                    //ex. int ary[literalExpr1] --> int ary ary[literalExpr1]
                    if(pre_pair.lexemeNode.getRef_kind().equals("Define") || pre_pair.lexemeNode.getRef_kind().equals("Init"))
                        resStack.push(pre_pair);
                    aryExpr.setNestedArrayExpression(null);
                    aryExpr.setIndexExpression(stack.get(currentindex+1).expression);
                    aryExpr.setAtomic(idExpr);
                }
                //ex. num[3][4] : arrayExpr + [ + literExpr + ]
                else if (pre_pair.expression != null && pre_pair.expression instanceof ArrayExpression) {
                    aryExpr = new ArrayExpression();
                    aryExpr.setNestedArrayExpression((ArrayExpression) pre_pair.expression);
                    aryExpr.setIndexExpression(stack.get(currentindex+1).expression);
                    aryExpr.setAtomic(aryExpr.getNestedArrayExpression().getAtomic());
                }
                startIndex += 2;
                //ex. int ary[literalExpr1] --> int ary ary[literalExpr1] --> int ary aryExpr1
                resStack.push(new LxmExprPair(null,aryExpr));
            }
            else
                resStack.push(pair);
            currentindex++; startIndex++;
        }
        return resStack;
    }

    /** classify whether it is binary or unary operator **/
    Stack<LxmExprPair> classifyBinaryUnary(Stack<LxmExprPair> stack) {
        int currentIndex = 0;
        LxmExprPair currentPair, prePair = null, postPair = null;

        for (LxmExprPair pair : stack) {
            /* [&] [*] [+] [-] -> [&_] [_&_] [*_] [_*_] [+_] [_+_] [-_] [_-_] */
            if (pair.lexemeNode != null &&
                    (pair.lexemeNode.getData().equals("&") || pair.lexemeNode.getData().equals("*")
                            || pair.lexemeNode.getData().equals("+") || pair.lexemeNode.getData().equals("-"))) {
                currentPair = stack.get(currentIndex);
                //get pre pair
                if (currentIndex > 0)
                    prePair = stack.get(currentIndex - 1);
                //ex. [expr]&[expr] OR a&b : binaryOperator
                if (currentIndex > 0 &&
                        (prePair.expression != null || (prePair.lexemeNode != null &&
                                (!prePair.lexemeNode.getToken().equals("Operator")
                                   || prePair.lexemeNode.getData().contains("++")
                                   || prePair.lexemeNode.getData().contains("--")))))
                    stack.get(currentIndex).lexemeNode.setData("_" + currentPair.lexemeNode.getData() + "_");
                //ex. a + &b : unaryOperator
                else if (currentIndex == 0 ||
                        (prePair.lexemeNode != null && prePair.lexemeNode.getToken().equals("Operator")))
                    stack.get(currentIndex).lexemeNode.setData(currentPair.lexemeNode.getData() + "_");
            }

            /* [++] [--] -> [_++] [++_] [_--] [--_] */
            if (pair.lexemeNode != null && (pair.lexemeNode.getData().equals("++") || pair.lexemeNode.getData().equals("--"))) {
                if (currentIndex > 0)
                    prePair = stack.get(currentIndex - 1);
                if (currentIndex < stack.size() - 1)
                    postPair = stack.get(currentIndex + 1);

                currentPair = stack.get(currentIndex);
                // ex. a++
                if (prePair != null && (prePair.expression != null
                        || (prePair.lexemeNode!=null && prePair.lexemeNode.getToken().equals("Operator")==false)))
                    stack.get(currentIndex).lexemeNode.setData("_" + currentPair.lexemeNode.getData());
                // ex. ++a
                else if (postPair != null && (postPair.expression != null
                        || (postPair.lexemeNode!=null && postPair.lexemeNode.getToken().equals("Operator")==false)))
                    stack.get(currentIndex).lexemeNode.setData(currentPair.lexemeNode.getData() + "_");
            }
            currentIndex++;
        }
        return stack;
    }

    /** express some operators [ _++ _-- . -> ] before express castExpr **/
    Stack<LxmExprPair> expressSomeOperators (Stack<LxmExprPair> stack) {
        Stack<LxmExprPair> resStack = new Stack<>();
        Stack<LxmExprPair> sample;
        Expression leftChild, rightChild;
        LxmExprPair pre_pair, post_pair;
        int currentIndex=0, startIndex=0;

        for(LxmExprPair pair : stack) {
            if (currentIndex < startIndex) {
                currentIndex++;
                continue;
            }
            if(pair.lexemeNode!=null && pair.lexemeNode.getToken().equals("Operator")) {
                //when it is [ _++ _-- ]
                if (totalOperators.get(pair.lexemeNode.getData()) == 0) {
                    UnaryExpression unaryExpr = new UnaryExpression();
                    unaryExpr.setOperator(pair.lexemeNode.getData());
                    //left child
                    pre_pair = resStack.pop();
                    if(pre_pair.lexemeNode!=null) {
                        sample = new Stack<>();
                        sample.push(pre_pair);
                        leftChild = expressIdLiteral(sample).pop().expression;
                        unaryExpr.setOperand(leftChild);
                    }
                    else
                        unaryExpr.setOperand(pre_pair.expression);
                    //push unaryExpression
                    resStack.push(new LxmExprPair(null,unaryExpr));
                }
                //when it is [ . -> ]
                else if (totalOperators.get(pair.lexemeNode.getData()) == 1) {
                    BinaryExpression binaryExpr = new BinaryExpression();
                    binaryExpr.setOperator(pair.lexemeNode.getData());
                    //left child
                    pre_pair = resStack.pop();
                    if (pre_pair.lexemeNode != null) {
                        sample = new Stack<>();
                        sample.push(pre_pair);
                        leftChild = expressIdLiteral(sample).pop().expression;
                    } else
                        leftChild = pre_pair.expression;
                    binaryExpr.setLhsOperand(leftChild);
                    //right child
                    post_pair = stack.get(currentIndex + 1);
                    if (post_pair.lexemeNode != null) {
                        sample = new Stack<>();
                        sample.push(post_pair);
                        rightChild = expressIdLiteral(sample).pop().expression;
                    } else
                        rightChild = post_pair.expression;
                    binaryExpr.setRhsOperand(rightChild);
                    //push binaryExpression
                    resStack.push(new LxmExprPair(null, binaryExpr));
                    startIndex++;
                }
                else
                    resStack.push(pair);
            }
            else
                resStack.push(pair);
            currentIndex++; startIndex++;
        }
        return resStack;
    }

    /** make cast expression */
    Stack<LxmExprPair> expressCast(Stack<LxmExprPair> stack) {
        Stack<LxmExprPair> resStack = new Stack<>();
        LxmExprPair nextPair;
        int currentIndex = 0, startIndex = 0;

        for(LxmExprPair pair : stack) {
            if (currentIndex < startIndex) {
                currentIndex++;
                continue;
            }
            //ex. (int)number
            if (pair.lexemeNode != null && pair.lexemeNode.getToken().equals("Keyword")) {
                if(currentIndex >= stack.size()-1)
                    continue;
                nextPair = stack.get(currentIndex+1);
                //when it is real "cast"
                if(nextPair.lexemeNode != null && nextPair.lexemeNode.getData().equals(")")) {
                    CastExpression castExpr = new CastExpression();
                    castExpr.setType(pair.lexemeNode.getData());
                    nextPair = stack.get(currentIndex+2);
                    //ex. (int)callExpr
                    if(nextPair.expression != null) {
                        castExpr.setCast(nextPair.expression);
                        startIndex += 2;
                    }
                    //ex. (int)(binaryExpr)
                    else if(nextPair.lexemeNode != null && nextPair.lexemeNode.getData().equals("(")) {
                        nextPair = stack.get(currentIndex+3);
                        castExpr.setCast(nextPair.expression);
                        startIndex += 4;
                    }
                    //ex. (int)a
                    else if(nextPair.lexemeNode != null) {
                        ArrayList<LexemeNode> lexemeList = new ArrayList<>();
                        lexemeList.add(nextPair.lexemeNode);
                        //ex. set idExpression of 'a'
                        castExpr.setCast(getExprFromSplitedLex(lexemeList).get(0));
                        startIndex += 2;
                    }
                    resStack.push(new LxmExprPair(null, castExpr));
                }
            }
            //just others
            if ((pair.expression != null)
                    || (pair.lexemeNode != null
                    && (pair.lexemeNode.getData().equals("(") == false && pair.lexemeNode.getData().equals(")") == false)))
                resStack.push(pair);
            currentIndex++; startIndex++;
        }

        return resStack;
    }

    /** make identifier & literal expression **/
    Stack<LxmExprPair> expressIdLiteral(Stack<LxmExprPair> stack) {
        Stack<LxmExprPair> resStack = new Stack<>();

        for(LxmExprPair pair : stack) {
            if(pair.lexemeNode != null) {
                LexemeNode lex = pair.lexemeNode;
                //identifier expression
                if(lex.getToken().equals("Identifier") && lex.getRef_kind().equals("Type") == false && lex.getRef_kind().equals("Call") == false) {
                    IdExpression idExpr = searchVariable(lex.getData());
                    if (lex.getRef_kind().equals("Define") || lex.getRef_kind().equals("Init")) {
                        DeclExpression declExpr = new DeclExpression();
                        if(lex.getRef_kind().equals("Init"))
                            declExpr.setBinaryExpression(new BinaryExpression());
                        else if(lex.getRef_kind().equals("Define"))
                            declExpr.setBinaryExpression(null);
                        //int a = 3; --> declExpr1 idExpr1 = literalExpr1
                        declExpr.setExpression(idExpr);
                        //int ary[3]; --> idExpr1 aryExpr1 --> declExpr1 aryExpr1 --> declExpr1
                        /** 추가하기!! */
                        declExpr.setType(idExpr.getType().toString());
                        resStack.push(new LxmExprPair(null, declExpr));
                    }
                    resStack.push(new LxmExprPair(null, idExpr));
                }
                //literal expression
                else if(lex.getToken().equals("Literal")
                        || ((lex.getToken().equals("Identifier")) && isDeclaredInHeader(lex.getData())==true)
                        || (lex.getToken().equals("String"))) {
                    LiteralExpression literalExpr = new LiteralExpression();
                    literalExpr.setConstant(lex.getData());
                    resStack.push(new LxmExprPair(null, literalExpr));
                }
                //the other lexemes
                else
                    resStack.push(pair);
            }
            //expressions
            else
                resStack.push(pair);
        }
        return  resStack;
    }

    /** make operator's expression **/
    Stack<LxmExprPair> expressBinaryUnaryConditional(Stack<LxmExprPair> stack) {
        LexemeNode lex;
        Stack<LxmExprPair> resStack = new Stack<>();
        boolean flag = false;

        //infix into postfix
        stack = infixIntoPostfix(stack);
        //express operators
        for(LxmExprPair pair : stack) {
            if(pair.lexemeNode != null && pair.lexemeNode.getToken().equals("Operator")) {
                lex = pair.lexemeNode;
                //binary operator
                if(binaryOperator.indexOf(lex.getData()) != -1) {
                    BinaryExpression binaryExpr = new BinaryExpression();
                    binaryExpr.setOperator(lex.getData());
                    binaryExpr.setRhsOperand(resStack.pop().expression);
                    binaryExpr.setLhsOperand(resStack.pop().expression);
                    resStack.push(new LxmExprPair(null,binaryExpr));
                }
                //single(unary) operator
                else if(singleOperator.indexOf(lex.getData()) != -1) {
                    UnaryExpression unaryExpr = new UnaryExpression();
                    unaryExpr.setOperator(lex.getData());
                    unaryExpr.setOperand(resStack.pop().expression);
                    resStack.push(new LxmExprPair(null,unaryExpr));
                }
                //conditional operator
                else if(lex.getData().equals(":")) {
                    if(!flag)
                        continue;
                    ConditionalExpression conditionalExpr = new ConditionalExpression();
                    conditionalExpr.setFalseExpr(resStack.pop().expression);
                    conditionalExpr.setTrueExpr(resStack.pop().expression);
                    resStack.push(new LxmExprPair(null,conditionalExpr));
                }
                else if(lex.getData().equals("?") && resStack.peek().expression!=null
                        && resStack.peek().expression instanceof ConditionalExpression){
                    ConditionalExpression conditionalExpr = (ConditionalExpression) resStack.pop().expression;
                    conditionalExpr.setCondition(resStack.pop().expression);
                    resStack.push(new LxmExprPair(null,conditionalExpr));
                    flag = true;
                }
            }
            else
                resStack.push(pair);
        }
        return resStack;
    }

    /** fill up the declaration expression **/
    Stack<LxmExprPair> expressDecl(Stack<LxmExprPair> stack) {
        Stack<LxmExprPair> resStack = new Stack<>();
        DeclExpression declExpr;
        LxmExprPair top;

        for(LxmExprPair pair : stack) {
            if(resStack.isEmpty() == true) {
                resStack.push(pair); continue;
            }
            top = resStack.peek();
            if(top.expression!=null && top.expression instanceof DeclExpression) {
                declExpr = (DeclExpression)resStack.pop().expression;
                //Init : ex. declExpr + binaryExpr
                if(pair.expression!=null && pair.expression instanceof BinaryExpression)
                    declExpr.setBinaryExpression((BinaryExpression)pair.expression);
                //array Define : ex. int ary[3][4]; -> declExpr + arrayExpr
                else if(pair.expression!=null && pair.expression instanceof ArrayExpression)
                    declExpr.setExpression(pair.expression);
                resStack.push(new LxmExprPair(null,declExpr));
            }
            else
                resStack.push(pair);
        }
        return resStack;
    }


    /***********************
     * Sub of Sub Functions*/
    /** get lexeme list which are in bracket */
    ArrayList<LexemeNode> getLexUntilEndBracket(ArrayList<LexemeNode> lexeme, int startIndex) {
        int curIndex=0;
        int count=0;
        ArrayList<LexemeNode> lexemeOfBracket = new ArrayList<>();

        //get lexemeList "a+b" from "(a+b)"
        //lexeme[startIndex] is "("
        for(LexemeNode lex : lexeme) {
            if(curIndex <= startIndex) {
                curIndex++; continue;
            }
            if(lex.getData().equals("(") || lex.getData().equals("["))    count++;
            else if (lex.getData().equals(")") || lex.getData().equals("]"))  count--;

            //when find end of bracket
            //now startIndex is ")"
            if(count == -1) break;
            curIndex++;
            lexemeOfBracket.add(lex);
        }
        return lexemeOfBracket;
    }

    /** make infix to postfix **/
    Stack<LxmExprPair> infixIntoPostfix(Stack<LxmExprPair> infix) {
        Stack<LxmExprPair> postfix = new Stack<>();
        Stack<LexemeNode> operatorStack = new Stack<>();
        for(LxmExprPair pair : infix) {
            //when its not operator
            if(pair.expression != null)
                postfix.push(pair);
                //when its operator
            else if(pair.lexemeNode != null) {
                LexemeNode lex = pair.lexemeNode;
                if(totalOperators.containsKey(lex.getData())) {
                    if(operatorStack.isEmpty() == true)
                        operatorStack.push(lex);
                        //ex. a = 1*5  ->  '*' < '='  ->  push '*'
                    else if(totalOperators.get(lex.getData())
                            < totalOperators.get(operatorStack.peek().getData())) {
                        operatorStack.push(lex);
                    }
                    //ex. 1+5-4  ->  '-' >= '+'  ->  pop '+' and push '-'
                    else if(totalOperators.get(lex.getData())
                            >= totalOperators.get(operatorStack.peek().getData())) {
                        postfix.push(new LxmExprPair(operatorStack.pop(),null));
                        operatorStack.push(lex);
                    }
                }
            }
        }
        while(operatorStack.isEmpty() != true) {
            postfix.push(new LxmExprPair(operatorStack.pop(),null));
        }
        return postfix;
    }

    /** Search Function information **/
    Function searchFunction(String searching_name) {
        for(Function function : functionInformations)
            if(function.getName().equals(searching_name))
                return  function;
        Function function = new Function();
        function.setName("NULL");
        return function;
    }

    /** ex. #define true 1 => true is LiteralExpression **/
    boolean isDeclaredInHeader(String name) {
        /**
         * */
        return false;
    }

    /** to know idExpression's scope and type **/
    IdExpression searchVariable(String searching_name) {
        for(IdExpression idExpr : variables) {
            if(idExpr.getName().equals(searching_name))
                return idExpr;
        }
        IdExpression idExpr = new IdExpression(searching_name,"global");
        idExpr.setType(new Type("UnknownType"));
        return idExpr;
    }

    /***************
     * print result*/
    public void showResult() {
        //title
        System.out.print("\n\n**********||");
        System.out.print(" Expression Of Function [ "+thisFunctionsName+" ] ");
        System.out.print("||**********\n");

        char c = 0;
        for(int i=0; i<setOfExpression.size(); i++) {
            for(Expression expr : setOfExpression.get(i))
                System.out.println(expr.getRawData(c));
        }
        System.out.println("*********************************************************\n");
    }

    public void printPhase(List<LxmExprPair> stack) {
        for(LxmExprPair p : stack) {
            if(p.lexemeNode != null)
                System.out.print(p.lexemeNode.getData() + " ");
            else if(p.expression != null)
                System.out.print("{" + p.expression.getClass() + "} ");
        }
        System.out.println();
    }
}
