package dev.capslock.scalapgpbootstrap

import ox.{ExitCode, Ox, supervised}
import cue4s.*

import java.io.Closeable

object Main extends ox.OxApp {

  def run(args: Vector[String])(using Ox): ExitCode = {
    GitHub.ensureGhCommandAvailable()
    GitHub.ensureInsideGitHubRepo { (owner, name) =>
      GitHub.withGitHubUserScopeAuthorized {
        GitHub.withUserEmailSelected { email =>
          scribe.info(s"Selected email: $email")

          val passPhrase          = Util.genRandomString(32)
          val keyName             = s"$owner/$name CI bot"
          val gpgBatchFileContent =
            Gpg.hydrateGpgBatchFileContent(keyName, passPhrase, email)

          supervised {
            val batchFilePath = ox.useCloseableInScope(
              Util.makeTemporaryFile(gpgBatchFileContent),
            )
            val passPhrasePath = ox.useCloseableInScope(
              Util.makeTemporaryFile(passPhrase),
            )
            val generatedKeyId              = Gpg.generateKeyPairFromBatchFile(keyName, batchFilePath.path)
            val privateKeyBase64            = Gpg.exportPrivateKeyAsBase64(keyName, passPhrasePath.path)
            val confirmedPublishKeyToServer = Prompts.sync.use { prompts =>
              prompts.confirm(
                "Do you want to publish the GPG key now?",
              )
            }
            if (confirmedPublishKeyToServer.toOption.getOrElse(false)) {
              scribe.info("Publishing GPG key to key servers...")
              ox.par(
                Gpg.publishPublicKeyToKeyServer(generatedKeyId, "hkps://keys.openpgp.org"),
                Gpg.publishPublicKeyToKeyServer(generatedKeyId, "hkps://keyserver.ubuntu.com"),
              )
            } else {
              scribe.info("Skipping publishing GPG key to key servers.")
            }

            val confirmedSetupGitHubSecret = Prompts.sync.use { prompts =>
              prompts.confirm(
                "Do you want to setup the GPG private key as GitHub Secret now?",
              )
            }
            if (confirmedSetupGitHubSecret.toOption.getOrElse(false)) {
              scribe.info("Setting up GPG private key as GitHub Secret...")
              ox.par(
                GitHub.setGitHubSecret(
                  "PGP_PASSPHRASE",
                  passPhrase,
                ),
                GitHub.setGitHubSecret(
                  "PGP_SECRET",
                  privateKeyBase64,
                ),
              )
            } else {
              scribe.info("Skipping setting up GitHub Secret.")
            }

            val confirmedRemoveKeyringFile = Prompts.sync.use { prompts =>
              prompts.confirm(
                "Do you want to remove the local keyring.gpg file now?",
              )
            }
            if (confirmedRemoveKeyringFile.toOption.getOrElse(false)) {
              scribe.info("Removing keyring.gpg file...")
              Gpg.wipeoutKeyring()
            } else {
              scribe.info("Skipping removing keyring.gpg file.")
            }

            scribe.info("All done!")

            ExitCode.Success
          }
        }
      }
    }
  }

}
