/**
 * Copyright (C) 2016 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.multideposit

import java.io.File
import javax.naming.Context
import javax.naming.ldap.InitialLdapContext

import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.apache.commons.configuration.PropertiesConfiguration
import org.rogach.scallop.{ScallopConf, ScallopOption}

import scala.util.{Failure, Success, Try}

object CommandLineOptions extends DebugEnhancedLogging {

  def parse(args: Array[String]): Settings = {
    debug("Loading application.properties ...")
    val homeDir = new File(System.getProperty("app.home"))
    val props = {
      val ps = new PropertiesConfiguration()
      ps.setDelimiterParsingDisabled(true)
      ps.load(new File(homeDir, "cfg/application.properties"))
      ps
    }
    debug("Parsing command line ...")
    val opts = new ScallopCommandLine(props, args)

    val settings = Settings(
      multidepositDir = opts.multiDepositDir(),
      springfieldInbox = opts.springfieldInbox(),
      stagingDir = opts.stagingDir(),
      outputDepositDir = opts.outputDepositDir(),
      datamanager = opts.datamanager(),
      depositPermissions = DepositPermissions(props.getString("deposit.permissions.access"), props.getString("deposit.permissions.group")),
      ldap = {
        val env = new java.util.Hashtable[String, String]
        env.put(Context.PROVIDER_URL, props.getString("auth.ldap.url"))
        env.put(Context.SECURITY_AUTHENTICATION, "simple")
        env.put(Context.SECURITY_PRINCIPAL, props.getString("auth.ldap.user"))
        env.put(Context.SECURITY_CREDENTIALS, props.getString("auth.ldap.password"))
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")

        LdapImpl(new InitialLdapContext(env, null))
      })

    debug(s"Using the following settings: $settings")

    settings
  }
}

class ScallopCommandLine(props: PropertiesConfiguration, args: Array[String]) extends ScallopConf(args) {

  appendDefaultToDescription = true
  editBuilder(_.setHelpWidth(110))

  printedName = "easy-split-multi-deposit"
  version(s"$printedName ${Version()}")
  val description = "Splits a Multi-Deposit into several deposit directories for subsequent ingest into the archive"
  val synopsis = s"""$printedName.sh [{--springfield-inbox|-s} <dir>] [{--staging-dir|-d} <dir>] <multi-deposit-dir> <output-deposits-dir> <datamanager>"""
  banner(s"""
           |  $description
           |  Utility to process a Multi-Deposit prior to ingestion into the DANS EASY Archive
           |
           |Usage:
           |
           |  $synopsis
           |
           |Options:
           |""".stripMargin)

  val multiDepositDir: ScallopOption[File] = trailArg[File](
    name = "multi-deposit-dir",
    required = true,
    descr = "Directory containing the Submission Information Package to process. "
      + "This must be a valid path to a directory containing a file named "
      + s"'$instructionsFileName' in RFC4180 format.")

  val springfieldInbox: ScallopOption[File] = opt[File](
    name = "springfield-inbox",
    short = 's',
    descr = "The inbox directory of a Springfield Streaming Media Platform installation. " +
      "If not specified the value of 'springfield-inbox' in 'application.properties' is used.",
    default = Some(new File(props.getString("springfield-inbox"))))

  val stagingDir: ScallopOption[File] = opt[File](
    name = "staging-dir",
    short = 'd', // TODO make this 's' once the springfieldInbox argument has been removed
    descr = "A directory in which the deposit directories are created, after which they will be " +
      "moved to the 'deposit-dir'. If not specified, the value of 'staging-dir' in " +
      "'application.properties' is used.",
    default = Some(new File(props.getString("staging-dir"))))

  val outputDepositDir: ScallopOption[File] = trailArg[File](
    name = "deposit-dir",
    required = true,
    descr = "A directory to which the deposit directories are moved after the staging has been " +
      "completed successfully. The deposit directory layout is described in the easy-sword2 " +
      "documentation")

  val datamanager: ScallopOption[String] = trailArg[String](
    name = "datamanager",
    required = true,
    descr = "The username (id) of the datamanger (archivist) performing this deposit")

  validateFileExists(multiDepositDir)
  validateFileIsDirectory(multiDepositDir)
  validate(multiDepositDir)(dir => {
    val instructionFile: File = multiDepositInstructionsFile(dir)
    if (!dir.directoryContains(instructionFile))
      Left(s"No instructions file found in this directory, expected: $instructionFile")
    else
      Right(())
  })

  validateFileExists(springfieldInbox)
  validateFileIsDirectory(springfieldInbox)

  validateFileExists(outputDepositDir)
  validateFileIsDirectory(outputDepositDir)

  verify()
}
