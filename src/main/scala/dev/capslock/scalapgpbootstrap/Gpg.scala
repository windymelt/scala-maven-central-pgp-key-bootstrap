package dev.capslock.scalapgpbootstrap

import ox.Ox

object Gpg {

  opaque type KeyId = String

  object KeyId {
    def apply(id: String): KeyId = id

    extension (keyId: KeyId) {
      def value: String = keyId
    }

  }

  def hydrateGpgBatchFileContent(keyName: String, passPhrase: String, email: String): String =
    s"""
      |Key-Type: EDDSA
      |Key-Curve: ed25519
      |Key-Usage: sign
      |Passphrase: $passPhrase
      |Name-Real: $keyName
      |Name-Email: $email
      |Expire-Date: 0
    """.stripMargin

  def generateKeyPairFromBatchFile(keyName: String, batchFilePath: os.Path)(using Ox): KeyId = {
    import KeyId.*

    val cmd =
      Seq("gpg", "--no-default-keyring", "--keyring", "./keyring.gpg", "--batch", "--gen-key", batchFilePath.toString())
    val process     = os.proc(cmd).call()
    val listKeysCmd =
      Seq("gpg", "--no-default-keyring", "--keyring", "./keyring.gpg", "--list-secret-keys", "--with-colons")
    val listKeysProcess = os.proc(listKeysCmd).call()
    val outputLines     = listKeysProcess.out.lines()
    val keyIdOpt        = outputLines
      .sliding(4)
      .find { lines =>
        lines.exists(line => line.contains(s"uid") && line.contains(keyName))
      }
      .flatMap { lines =>
        lines.find(line => line.startsWith("fpr:")).map { fprLine =>
          val parts = fprLine.split(":")
          parts(9) // The 10th field is the fingerprint
        }
      }
    if (keyIdOpt.isEmpty) {
      scribe.error("Failed to retrieve the generated GPG key ID.")
      throw new RuntimeException("GPG key generation failed.")
    }
    val keyId = KeyId(keyIdOpt.get)
    scribe.info(s"Generated GPG Key ID: ${keyId.value}")
    keyId
  }

  def exportPrivateKeyAsBase64(keyName: String, passPhrasePath: os.Path)(using Ox): String = {
    val exportCmd = Seq(
      "gpg",
      "--batch",
      "--pinentry-mode",
      "loopback",
      "--passphrase-file",
      passPhrasePath.toString(),
      "--no-default-keyring",
      "--keyring",
      "./keyring.gpg",
      "--export-secret-keys",
      keyName,
    )
    val exportProcess    = os.proc(exportCmd).call()
    val privateKeyBinary = exportProcess.out.bytes
    val privateKeyBase64 = java.util.Base64.getEncoder.encodeToString(privateKeyBinary)

    privateKeyBase64
  }

  def publishPublicKeyToKeyServer(keyId: KeyId, keyServer: String)(using Ox): Unit = {
    import KeyId.*

    val cmd = Seq(
      "gpg",
      "--batch",
      "--no-default-keyring",
      "--keyring",
      "./keyring.gpg",
      "--keyserver",
      keyServer,
      "--send-key",
      keyId.value,
    )
    os.proc(cmd).call()
    scribe.info(s"Published public key ${keyId.value} to key server $keyServer")
  }

  def wipeoutKeyring()(using Ox): Unit = {
    val cmd = Seq(
      "rm",
      "-f",
      "./keyring.gpg",
    )
    os.proc(cmd).call()
    scribe.info("Wiped out keyring.gpg")

    val cmd2 = Seq(
      "rm",
      "-f",
      "./keyring.gpg~",
    )
    try {
      os.proc(cmd2).call()
      scribe.info("Wiped out keyring.gpg~")
    } catch {
      case _: Throwable =>
        scribe.error("Failed to wipe out keyring.gpg~")
    }
  }

}
