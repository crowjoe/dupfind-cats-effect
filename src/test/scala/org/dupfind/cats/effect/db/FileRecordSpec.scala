package org.dupfind.cats.effect.db

import java.time.Instant

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class FileRecordSpec extends AnyFunSpec with Matchers {
  describe("FileRecord") {
    val present = Instant.ofEpochSecond(1000L)
    val past = present.minusSeconds(60)
    val future = present.plusSeconds(60)
    val record = FileRecord("filename", "checksum", present)

    describe("expired") {
      it("should return true when checked at a future time") {
        record.expired(future) shouldBe true
      }

      it("should return false when checked at the present time") {
        record.expired(present) shouldBe false
      }

      it("should return false when checked at a past time") {
        record.expired(past) shouldBe false
      }
    }

    describe("unexpired") {
      it("should return false when checked at a future time") {
        record.unexpired(future) shouldBe false
      }

      it("should return true when checked at the present time") {
        record.unexpired(present) shouldBe true
      }

      it("should return true when checked at a past time") {
        record.unexpired(past) shouldBe true
      }
    }
  }
}
