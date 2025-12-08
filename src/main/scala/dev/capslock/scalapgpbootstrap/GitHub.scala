package dev.capslock.scalapgpbootstrap

import cue4s.*
import ox.Ox
import ox.ExitCode
import io.circe.parser.parse

object GitHub {

  def ensureGhCommandAvailable()(using Ox): Unit = {
    try {
      os.proc("gh", "--version").call()
    } catch {
      case _: Throwable =>
        scribe.error(
          "GitHub CLI (gh) is not installed or not available in PATH.",
        )
        println(
          "Please install GitHub CLI from https://cli.github.com/ and ensure it is available in your PATH.",
        )
        throw new Exception("GitHub CLI not available")
    }
  }

  def withGitHubUserScopeAuthorized(
      body: => Ox ?=> ExitCode,
  )(using Ox): ExitCode = {
    gitHubAuthScopes() match {
      case Some(scopes) if scopes.contains("user") =>
        scribe.info("GitHub authentication token has 'user' scope.")
        body
      case _ =>
        scribe.warn(
          "GitHub authentication token does not have 'user' scope.",
        )
        println("You should login to GitHub CLI with 'user' scope.")

        val confirmed = Prompts.sync.use { prompts =>
          prompts.confirm(
            "Do you want to refresh GitHub authentication token to include 'user' scope now? (You may be prompted to login again)",
          )
        }

        if (!confirmed.toOption.getOrElse(false)) {
          scribe.error("Aborting.")
          return ExitCode.Failure(1)
        }
        scribe.info(
          "Refreshing GitHub authentication token to include 'user' scope...",
        )
        refreshGitHubAuthToken()
        body
    }
  }

  def withUserEmailSelected(body: Ox ?=> String => ExitCode)(using Ox): ExitCode = {
    val emails        = GitHub.retreiveGitHubUserEmails()
    val selectedEmail = Prompts.sync.use { prompts =>
      val emailsDisplay = emails.map { case (email, isPublic) =>
        if (isPublic) s"$email (public)" else s"$email (private)"
      }.toList
      val emailsDisplayToEmail = emailsDisplay.zip(emails).toMap
      val choice               = prompts
        .singleChoice(
          "Select an email to associate with the GPG key:",
          emailsDisplay,
        )
        .toOption
      choice.flatMap(emailsDisplayToEmail.get).map(_.email)
    }

    selectedEmail match {
      case None =>
        scribe.error("No email selected. Aborting.")
        ExitCode.Failure(1)
      case Some(email) =>
        scribe.info(s"Selected email: $email")
        body(email)
    }
  }

  def gitHubAuthScopes(): Option[Seq[String]] = try {
    val result     = os.proc("gh", "auth", "status").call()
    val resultText = result.out.text()
    resultText.split("\n").find(_.contains("Token scopes:")) match {
      case Some(line) =>
        val scopes = line.dropWhile(_ != ':').tail.trim().split(", ").collect { case s"'$scope'" =>
          scope
        }
        scribe.debug(s"GitHub authentication token has the following scopes: ${scopes.mkString(", ")}")
        Some(scopes)
      case None => None
    }
  } catch {
    case _: Throwable => None
  }

  def refreshGitHubAuthToken(): Unit = {
    os.proc("gh", "auth", "login", "-h", "github.com", "-s", "user").call()
  }

  def ensureInsideGitHubRepo(body: => Ox ?=> (owner: String, name: String) => ExitCode)(using Ox): ExitCode = {
    val repoInfo      = os.proc("gh", "repo", "view", "--json", "nameWithOwner").call()
    val repoJsonText  = repoInfo.out.text()
    val nameWithOwner = parse(repoJsonText)
      .flatMap(json => json.hcursor.get[String]("nameWithOwner"))
      .getOrElse {
        scribe.error("Failed to get repository information from GitHub CLI.")
        throw new Exception("Not inside a GitHub repository")
      }
    val Array(owner, name) = nameWithOwner.split("/", 2)
    body(owner, name)
  }

  case class GitHubEmailResponse(email: String, verified: Boolean, primary: Boolean, visibility: Option[String])
      derives io.circe.Decoder

  def retreiveGitHubUserEmails(): Seq[(email: String, isPublic: Boolean)] = {
    val emailsInfo     = os.proc("gh", "api", "/user/emails").call()
    val emailsJsonText = emailsInfo.out.text()
    parse(emailsJsonText)
      .flatMap(json => json.as[Seq[GitHubEmailResponse]])
      .getOrElse(Seq.empty)
      .collect { case GitHubEmailResponse(email, true, _, isPublic) =>
        (email = email, isPublic = isPublic.contains("public"))
      }
  }

  def setGitHubSecret(name: String, value: String): Unit = {
    val cmd = Seq(
      "gh",
      "secret",
      "set",
      name,
      "--body",
      value,
    )
    os.proc(cmd).call()
  }

}
