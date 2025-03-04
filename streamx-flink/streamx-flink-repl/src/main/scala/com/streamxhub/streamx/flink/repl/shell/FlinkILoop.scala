/*
 * Copyright 2019 The StreamX Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.streamxhub.streamx.flink.repl.shell

import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.util.AbstractID

import java.io.{BufferedReader, File, FileOutputStream}
import scala.tools.nsc.interpreter._


class FlinkILoop(
                  val flinkConfig: Configuration,
                  val externalJars: Option[Array[String]],
                  in0: Option[BufferedReader],
                  out0: JPrintWriter)
  extends ILoop(in0, out0) {

  def this(
            flinkConfig: Configuration,
            externalJars: Option[Array[String]],
            in0: BufferedReader,
            out: JPrintWriter) {
    this(flinkConfig, externalJars, Some(in0), out)
  }

  def this(
            flinkConfig: Configuration,
            externalJars: Option[Array[String]]) {
    this(flinkConfig, externalJars, None, new JPrintWriter(Console.out, true))
  }

  def this(
            flinkConfig: Configuration,
            in0: BufferedReader,
            out: JPrintWriter) {
    this(flinkConfig, None, in0, out)
  }

  // remote environment
  private val remoteSenv: ScalaShellStreamEnvironment = {
    // allow creation of environments
    ScalaShellEnvironment.resetContextEnvironments()
    ScalaShellStreamEnvironment.resetContextEnvironments()
    val remoteSenv = new ScalaShellStreamEnvironment(flinkConfig, this, getExternalJars(): _*)
    // prevent further instantiation of environments
    ScalaShellEnvironment.disableAllContextAndOtherEnvironments()
    ScalaShellStreamEnvironment.disableAllContextAndOtherEnvironments()
    remoteSenv
  }

  // local environment
  val scalaSenv: StreamExecutionEnvironment = new StreamExecutionEnvironment(remoteSenv)

  /**
   * creates a temporary directory to store compiled console files
   */
  private val tmpDirBase: File = {
    // get unique temporary folder:
    val abstractID: String = new AbstractID().toString
    val tmpDir: File = new File(
      System.getProperty("java.io.tmpdir"),
      "scala_shell_tmp-" + abstractID)
    if (!tmpDir.exists) {
      tmpDir.mkdir
    }
    tmpDir
  }

  // scala_shell commands
  private val tmpDirShell: File = {
    new File(tmpDirBase, "scala_shell_commands")
  }

  // scala shell jar file name
  private val tmpJarShell: File = {
    new File(tmpDirBase, "scala_shell_commands.jar")
  }

  private val packageImports = Seq[String](
    "org.apache.flink.core.fs._",
    "org.apache.flink.core.fs.local._",
    "org.apache.flink.api.common.io._",
    "org.apache.flink.api.common.aggregators._",
    "org.apache.flink.api.common.accumulators._",
    "org.apache.flink.api.common.distributions._",
    "org.apache.flink.api.common.operators._",
    "org.apache.flink.api.common.operators.base.JoinOperatorBase.JoinHint",
    "org.apache.flink.api.common.functions._",
    "org.apache.flink.api.java.io._",
    "org.apache.flink.api.java.aggregation._",
    "org.apache.flink.api.java.functions._",
    "org.apache.flink.api.java.operators._",
    "org.apache.flink.api.java.sampling._",
    "org.apache.flink.api.scala._",
    "org.apache.flink.api.scala.utils._",
    "org.apache.flink.streaming.api.scala._",
    "org.apache.flink.streaming.api.windowing.time._",
    "org.apache.flink.table.api._",
    "org.apache.flink.table.api.bridge.scala._",
    "org.apache.flink.types.Row"
  )

  override def createInterpreter(): Unit = {
    super.createInterpreter()

    intp.beQuietDuring {
      // import dependencies
      intp.interpret("import " + packageImports.mkString(", "))

      // set execution environment
      intp.bind("env", this.scalaSenv)
    }
  }

  /**
   * Packages the compiled classes of the current shell session into a Jar file for execution
   * on a Flink cluster.
   *
   * @return The path of the created Jar file
   */
  def writeFilesToDisk(): File = {
    val vd = intp.virtualDirectory

    val vdIt = vd.iterator

    for (fi <- vdIt) {
      if (fi.isDirectory) {

        val fiIt = fi.iterator

        for (f <- fiIt) {

          // directory for compiled line
          val lineDir = new File(tmpDirShell.getAbsolutePath, fi.name)
          lineDir.mkdirs()

          // compiled classes for commands from shell
          val writeFile = new File(lineDir.getAbsolutePath, f.name)
          val outputStream = new FileOutputStream(writeFile)
          val inputStream = f.input

          // copy file contents
          org.apache.commons.io.IOUtils.copy(inputStream, outputStream)

          inputStream.close()
          outputStream.close()
        }
      }
    }

    val compiledClasses = new File(tmpDirShell.getAbsolutePath)

    val jarFilePath = new File(tmpJarShell.getAbsolutePath)

    val jh: JarHelper = new JarHelper
    jh.jarDir(compiledClasses, jarFilePath)

    jarFilePath
  }

  def getExternalJars(): Array[String] = externalJars.getOrElse(Array.empty[String])
}

