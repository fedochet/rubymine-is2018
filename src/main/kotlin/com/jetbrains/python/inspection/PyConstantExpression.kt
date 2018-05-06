package com.jetbrains.python.inspection

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
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
            val condition = pyIfPart.condition
            when (condition) {
                is PyBoolLiteralExpression -> registerProblem(condition, "The condition is always " + condition.value)
                is PyBinOp -> handleBinaryExpression(condition)
            }
        }

        private fun handleBinaryExpression(binOp: PyBinOp) {
            val evaledOp = evalBinaryExpressionToBool(binOp) ?: return

            registerProblem(binOp, "The condition is always $evaledOp")
        }

        companion object {
            fun evalBinaryExpressionToBool(condition: PyBinOp): Boolean? {
                val left = condition.leftExpression ?: return null
                val right = condition.rightExpression ?: return null
                val op = condition.psiOperator?.text ?: return null

                if (left is PyNum && right is PyNum) {
                    val leftNum = left.bigDecimalValue ?: return null
                    val rightNum = right.bigDecimalValue ?: return null

                    return when (op) {
                        ">" -> leftNum > rightNum
                        ">=" -> leftNum >= rightNum

                        "<" -> leftNum < rightNum
                        "=<" -> leftNum <= rightNum

                        "==" -> leftNum == rightNum
                        "!=" -> leftNum != rightNum

                        else -> null
                    }
                }

                return null
            }
        }
    }
}
