package dev.capslock.scalapgpbootstrap

import scala.jdk.CollectionConverters.given
import java.io.Closeable

object Util {

  def genRandomString(length: Int): String = {
    java.security.SecureRandom
      .getInstanceStrong()
      .ints(0, ACCEPTED_CHARS.size)
      .iterator()
      .asScala
      .take(length)
      .map((n: Integer) => ACCEPTED_CHARS.apply(n.toInt))
      .mkString
  }

  // no backslash to avoid escaping issues
  private val ACCEPTED_CHARS: Array[Char] = Array(
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
    'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
    'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '!', '"', '#', '$', '%', '&', '(',
    ')', '*', '+', ',', '-', '.', '/', ':', ';', '<', '=', '>', '?', '@', '[', ']', '^', '_', '`', '{', '|', '}', '~',
  )

  case class TemporaryFile(path: os.Path) extends Closeable {
    def close(): Unit = os.remove(path)
  }

  def makeTemporaryFile(content: String): TemporaryFile = {
    val batchFilePath = os.temp(prefix = "scala-pgp-bootstrap-gpg-batch-", suffix = ".txt")
    os.write.over(batchFilePath, content)
    TemporaryFile(batchFilePath)
  }

}
