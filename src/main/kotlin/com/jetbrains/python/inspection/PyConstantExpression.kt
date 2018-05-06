package com.jetbrains.python.inspection

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.psi.*

private typealias PyNum = PyNumericLiteralExpression
private typealias PyBinOp = PyBinaryExpression

class PyConstantExpression : PyInspection() {

    override fun buildVisitor(holder: ProblemsHolder,
                              isOnTheFly: Boolean,
                              session: LocalInspectionToolSession): PsiElementVisitor {
        return Visitor(holder, session)
    }

    class Visitor(holder: ProblemsHolder?, session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {
        override fun visitPyIfStatement(node: PyIfStatement) {
            super.visitPyIfStatement(node)
            processIfPart(node.ifPart)
            for (part in node.elifParts) {
                processIfPart(part)
            }
        }

        private fun processIfPart(pyIfPart: PyIfPart) {
            val condition = pyIfPart.condition ?: return
            val evaledExpression = evaluateExpression(condition) ?: return

            registerProblem(condition, "The condition is always $evaledExpression")
        }

        companion object {
            private val BOOL_OPERATORS = setOf(PyTokenTypes.AND_KEYWORD, PyTokenTypes.OR_KEYWORD)

            fun evaluateExpression(expr: PyExpression): Boolean? {
                return when (expr) {
                    is PyBoolLiteralExpression -> expr.value
                    is PyBinOp -> evalBinaryExpressionToBool(expr)
                    else -> null
                }
            }

            fun evalBinaryExpressionToBool(condition: PyBinOp): Boolean? {
                val left = condition.leftExpression ?: return null
                val right = condition.rightExpression ?: return null
                val op = condition.operator ?: return null

                if (left is PyNum && right is PyNum) {
                    val leftNum = left.bigDecimalValue ?: return null
                    val rightNum = right.bigDecimalValue ?: return null

                    return when (op) {
                        PyTokenTypes.GT -> leftNum > rightNum
                        PyTokenTypes.GE -> leftNum >= rightNum

                        PyTokenTypes.LT -> leftNum < rightNum
                        PyTokenTypes.LE -> leftNum <= rightNum

                        PyTokenTypes.EQEQ -> leftNum == rightNum
                        PyTokenTypes.NE -> leftNum != rightNum

                        else -> null
                    }
                }

                if (op in BOOL_OPERATORS) {
                    val leftValue = evaluateExpression(left) ?: return null
                    val rightValue = evaluateExpression(right) ?: return null

                    return when (op) {
                        PyTokenTypes.AND_KEYWORD -> leftValue && rightValue
                        PyTokenTypes.OR_KEYWORD -> leftValue || rightValue
                        else -> null
                    }
                }

                return null
            }
        }
    }
}
