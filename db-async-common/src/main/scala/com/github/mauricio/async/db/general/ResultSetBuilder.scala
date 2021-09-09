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

package com.github.mauricio.async.db.general

import collection.immutable.IndexedSeq
import collection.compat.immutable.ArraySeq
import com.github.mauricio.async.db.{RowData, ResultSet}
import com.github.mauricio.async.db.util.Log

private[async] object ResultSetBuilder {
  final val log = Log.get[ResultSetBuilder.type]
}

private[async] class ResultSetBuilder[T <: ColumnData](
  val columnTypes: IndexedSeq[T]
) {

  def build(): ResultSet = {
    ResultSet(columnTypes, rowsBuilder.result())
  }

  // ResultSet is very likely to be accessed in another thread.
  private final val rowsBuilder = ArraySeq.newBuilder[RowData]
  private var currSize          = 0

  private val columnMapping: Map[String, Int] = this.columnTypes.indices
    .map(index => (this.columnTypes(index).name, index))
    .toMap

  def addRow(row: Array[Any]): Unit = {
    rowsBuilder += new ArrayRowData(
      currSize,
      this.columnMapping,
      row
    )
    currSize = currSize + 1
  }

}
