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
package com.streamxhub.streamx.common.util

import com.streamxhub.streamx.common.conf.ConfigConst._
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.client.{Connection, ConnectionFactory, Table}
import org.apache.hadoop.hbase.{HBaseConfiguration, TableName}
import org.apache.hadoop.security.UserGroupInformation

import java.util.Properties
import scala.collection.JavaConversions._

/**
 * @author benjobs
 */
class HBaseClient(func: () => Connection) extends Serializable {
  lazy val connection: Connection = func()

  def table(table: String): Table = connection.getTable(TableName.valueOf(table))
}

object HBaseClient {
  val conf: Configuration = HBaseConfiguration.create

  def apply(prop: Properties): HBaseClient = {
    val user = prop.remove(KEY_HBASE_AUTH_USER)
    prop.foreach(x => conf.set(x._1, x._2))
    new HBaseClient(() => {
      if (user != null) {
        UserGroupInformation.setConfiguration(conf)
        val remoteUser: UserGroupInformation = UserGroupInformation.createRemoteUser(user.toString)
        UserGroupInformation.setLoginUser(remoteUser)
      }
      val connection = ConnectionFactory.createConnection(conf)
      sys.addShutdownHook {
        connection.close()
      }
      connection
    })
  }
}
