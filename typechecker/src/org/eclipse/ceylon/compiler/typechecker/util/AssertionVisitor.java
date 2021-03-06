/********************************************************************************
 * Copyright (c) 2011-2017 Red Hat Inc. and/or its affiliates and others
 *
 * This program and the accompanying materials are made available under the 
 * terms of the Apache License, Version 2.0 which is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0 
 ********************************************************************************/
package org.eclipse.ceylon.compiler.typechecker.util;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.ceylon.common.OSUtil;
import org.eclipse.ceylon.compiler.typechecker.analyzer.AnalysisError;
import org.eclipse.ceylon.compiler.typechecker.analyzer.UnsupportedError;
import org.eclipse.ceylon.compiler.typechecker.analyzer.UsageWarning;
import org.eclipse.ceylon.compiler.typechecker.parser.LexError;
import org.eclipse.ceylon.compiler.typechecker.parser.ParseError;
import org.eclipse.ceylon.compiler.typechecker.tree.AnalysisMessage;
import org.eclipse.ceylon.compiler.typechecker.tree.Message;
import org.eclipse.ceylon.compiler.typechecker.tree.Node;
import org.eclipse.ceylon.compiler.typechecker.tree.Tree;
import org.eclipse.ceylon.compiler.typechecker.tree.UnexpectedError;
import org.eclipse.ceylon.compiler.typechecker.tree.Visitor;
import org.eclipse.ceylon.model.typechecker.model.Type;
import org.eclipse.ceylon.model.typechecker.model.Unit;

public class AssertionVisitor extends Visitor {
    
    private boolean expectingError = false;
    private String errorMessage;
    private boolean expectingWarning = false;
    private String warningType;
    private List<Message> foundErrors = new ArrayList<Message>();
    private int errors = 0;
    private int warnings = 0;
    private boolean usageWarnings = false;

    @Override
    public void visit(Tree.TypedDeclaration that) {
        if (that.getType()!=null) {
            checkType(that, that.getType().getTypeModel(), that.getType());
        }
        super.visit(that);
    }

    @Override
    public void visit(Tree.ExpressionStatement that) {
        checkType(that, that.getExpression().getTypeModel(), that.getExpression());
        super.visit(that);
    }
    
    protected void checkType(Tree.Statement that, Type type, Node typedNode) {
        for (Tree.CompilerAnnotation c: that.getCompilerAnnotations()) {
            if (c.getIdentifier().getText().equals("type")) {
                Tree.StringLiteral sl = c.getStringLiteral();
                if (sl==null) {
                    out(that, "missing asserted type");
                }
                else {
                    String expectedType = 
                            sl.getText()
                              .replace(" ", "");
                    if (typedNode==null || type==null || 
                            type.getDeclaration()==null) {
                        out(that, "type not known");
                    }
                    else {
                        String actualType = 
                                type.asString(false)
                                    .replace(" ", "");
                        String abbreviatedActualType = 
                                type.asString()
                                    .replace(" ", "");
                        if (!actualType.equals(expectedType) &&
                                !abbreviatedActualType.equals(expectedType)) {
                            String desc = "'" + abbreviatedActualType + "'";
                            if (!actualType.equals(abbreviatedActualType)) {
                                desc += " ('"+ actualType + "')";
                            }
                            out(that, "type " + desc +
                                    " not of expected type '" + 
                                    expectedType + "'");
                        }
                    }
                }
            }
        }
    }
    
    @Override
    public void visit(Tree.StatementOrArgument that) {
        if (ignore) {
            super.visit(that);
            return;
        }
        if (that instanceof Tree.Variable) {
            if (((Tree.Variable) that).getType() 
                    instanceof Tree.SyntheticVariable) {
                super.visit(that);
                return;
            }
        }
        if (that instanceof Tree.ForIterator) {
            super.visit(that);
            return;
        }
        if (that instanceof Tree.Destructure) {
            super.visit(that);
            return;
        }
        boolean b = expectingError;
        boolean c = expectingWarning;
        List<Message> f = foundErrors;
        expectingError = false;
        expectingWarning = false;
        errorMessage = null;
        warningType = null;
        foundErrors = new ArrayList<Message>();
        initExpectingError(that.getCompilerAnnotations());
        super.visit(that);
        checkErrors(that);
        expectingError = b;
        expectingWarning = c;
        errorMessage = null;
        warningType = null;
        foundErrors = f;
    }
    
    boolean ignore;
    
    @Override
    public void visit(Tree.ParameterDeclaration that) {
        boolean b = expectingError;
        boolean c = expectingWarning;
        List<Message> f = foundErrors;
        expectingError = false;
        expectingWarning = false;
        errorMessage = null;
        warningType = null;
        foundErrors = new ArrayList<Message>();
        initExpectingError(that.getTypedDeclaration().getCompilerAnnotations());
        ignore=true;
        super.visit(that);
        ignore=false;
        checkErrors(that);
        expectingError = b;
        expectingWarning = c;
        errorMessage = null;
        warningType = null;
        foundErrors = f;
    }
    
    @Override
    public void visit(Tree.CompilationUnit that) {
        expectingError = false;
        expectingWarning = false;
        errorMessage = null;
        warningType = null;
        foundErrors = new ArrayList<Message>();
        initExpectingError(that.getCompilerAnnotations());
        foundErrors.addAll(that.getErrors());
        checkErrors(that);
        foundErrors = new ArrayList<Message>();
        expectingError = false;
        expectingWarning = false;
        errorMessage = null;
        warningType = null;
        super.visitAny(that);
    }
    
//    @Override
//    public void visit(Tree.Declaration that) {
//        super.visit(that);
//        for (Tree.CompilerAnnotation c: that.getCompilerAnnotations()) {
//            if (c.getIdentifier().getText().equals("captured")) {
//                Declaration d = that.getDeclarationModel();
//                if (!d.isCaptured() && !d.isShared()) {
//                    out(that, "not captured");
//                }
//            }
//            if (c.getIdentifier().getText().equals("uncaptured")) {
//                Declaration d = that.getDeclarationModel();
//                if (d.isCaptured() || d.isShared()) {
//                    out(that, "captured");
//                }
//            }
//        }
//    }

    protected void out(PrintStream ps, String level, String message, String at, String of) {
        StringBuffer buf = new StringBuffer();
        if (level != null) {
            if (level.contains("error")) {
                String red = OSUtil.color(level, OSUtil.Color.red);
                buf.append(red);
            } else if (level.contains("warning")) {
                String yellow = OSUtil.color(level, OSUtil.Color.yellow);
                buf.append(yellow);
            } else {
                buf.append(level);
            }
            buf.append(" [");
            buf.append(message);
            buf.append("]");
        } else {
            buf.append(message);
        }
        if (at != null) {
            buf.append(" at ");
            buf.append(at);
        }
        if (of != null) {
            buf.append(" of ");
            String blue = OSUtil.color(of, OSUtil.Color.blue);
            buf.append(blue);
        }
        ps.println(buf.toString());
    }

    protected void out(PrintStream ps, String level, AnalysisMessage err) {
        out(ps, level, err.getMessage(),
                err.getTreeNode().getLocation(), file(err.getTreeNode()));
    }

    protected void out(Node that, String message) {
        out(System.err, null, message, that.getLocation(), file(that));
    }

    protected void out(Node that, LexError err) {
        errors++;
        out(System.err, "lex error", err.getMessage(), err.getHeader(), file(that));
    }

    protected void out(Node that, ParseError err) {
        errors++;
        out(System.err, "parse error", err.getMessage(), err.getHeader(), file(that));
    }

    protected void out(UnexpectedError err) {
        errors++;
        out(System.err, "unexpected error", err);
    }

    protected void out(AnalysisError err) {
        errors++;
        out(System.err, "error", err);
    }

    protected void out(UnsupportedError err) {
        warnings++;
        out(System.out, "warning", err);
    }

    /**
     * Prints warning messages for unused declarations.
     *
     * @param err error message
     */
    protected void out(UsageWarning err) {
        out(System.out, "warning", err);
    }

    private String file(Node that) {
        Unit unit = that.getUnit();
        if (unit==null) {
            return null;
        }
        else {
            String relativePath = unit.getRelativePath();
            return !relativePath.isEmpty() ?
                    relativePath : 
                    unit.getFilename();
        }
    }

    private void checkErrors(Node that) {
        try {
            for (Message err: foundErrors) {
                if (!includeError(err, 1)) {
                    continue;
                }
                if (err instanceof UnexpectedError) {
                    out( (UnexpectedError) err );
                }
            }
            if (expectingError) {
                boolean found = false;
                for (Message err: foundErrors) {
                    if (!includeError(err, 2)) {
                        continue;
                    }
                    if (err instanceof AnalysisError ||
                        err instanceof LexError ||
                        err instanceof ParseError) {
                        if (errorMessage==null ||
                                err.getMessage()
                                    .contains(errorMessage)) {
                            return;
                        }
                        else {
                            found = true;
                        }
                    }
                }
                if (found) {
                    out(that, "error message should contain \"" + 
                            errorMessage);
                }
                else {
                    out(that, "no errors");
                    return;
                }
            }
            if (expectingWarning) {
                boolean found = false;
                for (Message err: foundErrors) {
                    if (!includeError(err, 2)) {
                        continue;
                    }
                    if (err instanceof UsageWarning) {
                        if (warningType==null ||
                                ((UsageWarning) err).getWarningName()
                                    .equals(warningType)) {
                            return;
                        }
                        else {
                            found = true;
                        }
                    }
                }
                if (found) {
                    out(that, "warning type should be \"" + 
                            warningType);
                }
                else {
                    out(that, "no warnings");
                    return;
                }
            }
            for (Message err: foundErrors) {
                if (!includeError(err, 3)) {
                    continue;
                }
                if (err instanceof LexError) {
                    out( that, (LexError) err );
                }
                else if (err instanceof ParseError) {
                    out( that, (ParseError) err );
                }
                else if (err instanceof UnsupportedError) {
                    out( (UnsupportedError) err );
                } 
                else if (err instanceof AnalysisError) {
                    out( (AnalysisError) err );
                }
                else if (err instanceof UsageWarning) {
                    if (usageWarnings) {
                        out( (UsageWarning) err );
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    protected void initExpectingError(List<Tree.CompilerAnnotation> annotations) {
        for (Tree.CompilerAnnotation c: annotations) {
            if (c.getIdentifier().getText().equals("error")) {
                expectingError = true;
                Tree.StringLiteral sl = c.getStringLiteral();
                if (sl!=null) {
                    errorMessage = sl.getText();
                }
            }
            if (c.getIdentifier().getText().equals("warn")) {
                expectingWarning = true;
                Tree.StringLiteral sl = c.getStringLiteral();
                if (sl!=null) {
                    warningType = sl.getText();
                }
            }
        }
    }
    
    protected boolean includeError(Message err, int phase) {
        return true;
    }
    
    /**
     * Enables or disables output of the warnings for the unused declarations
     * 
     * @param usageWarnings true to enable output and false otherwise.
     */
    public void includeUsageWarnings(boolean usageWarnings) {
        this.usageWarnings = usageWarnings;
    }

    @Override
    public void visitAny(Node that) {
        foundErrors.addAll(that.getErrors());
        super.visitAny(that);
    }
    
    public void print(boolean verbose) {
        if(!verbose && errors == 0 && warnings == 0)
            return;
        System.out.println(errors + " errors, " + warnings + " warnings");
    }

    public List<Message> getFoundErrors() {
        return foundErrors;
    }

    public int getErrors() {
        return errors;
    }

    public int getWarnings() {
        return warnings;
    }
    
}
