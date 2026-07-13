package net.nmoncho.helenus.api.cql.dml.where

/** A WHERE-clause predicate. */
sealed class Predicate(val column: String, val operator: String, val value: String) extends WhereClause {
  def toCQL: String = s"$column $operator $value"
  override def toString: String = s"Predicate($toCQL)"
}
