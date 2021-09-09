/*
 * Copyright 2013 Maurício Linhares
 *
 * Maurício Linhares licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.github.mauricio.async.db

import com.github.mauricio.async.db.general.ColumnData
import scala.collection.immutable.IndexedSeq

/** Represents the collection of rows that is returned from a statement inside a {@link QueryResult}. It's basically
  * a collection of Array[Any]. Mutating fields in this array will not affect the database in any way
  */
trait ResultSet extends IndexedSeq[RowData] {

  /** The names of the columns returned by the statement.
    *
    * @return
    */
  def columnNames: IndexedSeq[String]

}

object ResultSet {

  def apply[T <: ColumnData](
    columnTypes: IndexedSeq[T],
    rows: IndexedSeq[RowData]
  ): ResultSet = {
    new ResultSetImpl(columnTypes, rows)
  }

  private class ResultSetImpl[T <: ColumnData](
    val columnTypes: IndexedSeq[T],
    rows: IndexedSeq[RowData]
  ) extends ResultSet {

    lazy val columnNames: IndexedSeq[String] = columnTypes.map(c => c.name)
    lazy val types: IndexedSeq[Int]          = columnTypes.map(_.dataType)

    def apply(i: Int) = rows(i)
    def length        = rows.size
  }
}
