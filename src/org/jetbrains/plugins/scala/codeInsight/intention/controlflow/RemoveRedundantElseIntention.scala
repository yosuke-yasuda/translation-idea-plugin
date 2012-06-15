package org.jetbrains.plugins.scala.codeInsight.intention.controlflow

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScBlockExpr, ScIfStmt}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScBlockImpl
import com.intellij.psi.codeStyle.CodeStyleManager

/**
 * @author Ksenia.Sautina
 * @since 6/8/12
 */

object RemoveRedundantElseIntention {
  def familyName = "Remove redundant Else"
}

class RemoveRedundantElseIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = RemoveRedundantElseIntention.familyName

  override def getText: String = "Remove redundant 'else'"

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val ifStmt: ScIfStmt = PsiTreeUtil.getParentOfType(element, classOf[ScIfStmt], false)
    if (ifStmt == null) return false

    val thenBranch = ifStmt.thenBranch.getOrElse(null)
    val elseBranch = ifStmt.elseBranch.getOrElse(null)
    val condition = ifStmt.condition.getOrElse(null)
    if (thenBranch == null || elseBranch == null || condition == null) return false

    val offset = editor.getCaretModel.getOffset
    if (!(thenBranch.getTextRange.getEndOffset <= offset && offset <= elseBranch.getTextRange.getStartOffset))
      return false

    thenBranch match {
      case tb: ScBlockExpr =>
        val lastExpr = tb.exprs(tb.exprs.size - 1) //TODO[ksenia]: try tb.lastExpr
        if (lastExpr.getText.trim.startsWith("return")) return true
        false
      case e: ScExpression =>
        if (e.getText.trim.startsWith("return")) return true
        false
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val ifStmt: ScIfStmt = PsiTreeUtil.getParentOfType(element, classOf[ScIfStmt], false)
    if (ifStmt == null || !ifStmt.isValid) return

    val start = ifStmt.getTextRange.getStartOffset
    val elseBranch = ifStmt.elseBranch.get
    val expr = new StringBuilder

    expr.append("if (").append(ifStmt.condition.get.getText).append(") ").append(ifStmt.thenBranch.get.getText)

    elseBranch match {
      case eb: ScBlockExpr => expr.append(eb.getText.trim.drop(1).dropRight(1))
      case _ => expr.append("\n").append(elseBranch.getText.trim)
    }

    val newExpr: ScBlockImpl = ScalaPsiElementFactory.createBlockExpressionWithoutBracesFromText(expr.toString(), element.getManager)

    inWriteAction {
      CodeStyleManager.getInstance(project).reformat(newExpr)
      val diff = newExpr.exprs(0).asInstanceOf[ScIfStmt].thenBranch.get.getTextRange.getEndOffset -
        newExpr.exprs(0).asInstanceOf[ScIfStmt].getTextRange.getStartOffset

      ifStmt.replaceExpression(newExpr, removeParenthesis = true)
      editor.getCaretModel.moveToOffset(start + diff)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
    //todo[ksenia]: this seems wrong
    //It's better to just delete else branch instead of replacing full if expression (with causing if expression reformatting)
  }
}
