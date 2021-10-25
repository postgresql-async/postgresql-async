package com.github.mauricio.async.db.postgresql

import com.github.mauricio.async.db.postgresql.column.PostgreSQLColumnEncoderRegistry
import com.github.mauricio.async.db.Spec

class PostgreSQLColumnEncoderRegistrySpec extends Spec {

  val encoder = new PostgreSQLColumnEncoderRegistry

  "column encoder registry" - {

    "encode Some(value) like value" in {

      val actual   = encoder.encode(Some(1L))
      val expected = encoder.encode(1L)

      actual mustEqual expected
    }

    "encode Some(value) in list like value in list" in {

      val actual   = encoder.encode(List(Some(1L), Some("foo")))
      val expected = encoder.encode(List(1L, "foo"))

      actual mustEqual expected
    }

    "encode None as null" in {
      val actual   = encoder.encode(None)
      val expected = encoder.encode(null)

      actual mustEqual expected
    }

    "determine kindOf Some(value) like kindOf value" in {
      val actual   = encoder.kindOf(Some(1L))
      val expected = encoder.kindOf(1L)

      actual mustEqual expected
    }

    "determine kindOf None like kindOf null" in {
      val actual   = encoder.kindOf(None)
      val expected = encoder.kindOf(null)

      actual mustEqual expected
    }

    "encodes Some(null) as null" in {
      val actual = encoder.encode(Some(null))
      actual mustEqual null
    }

    "encodes null as null" in {
      val actual = encoder.encode(null)
      actual mustEqual null
    }

  }

}
