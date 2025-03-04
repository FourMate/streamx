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

package com.streamxhub.streamx.flink.connector.http.conf


import java.util.Properties

/**
 * @author benjobs
 */
object HttpConfigOption {

  val HTTP_SINK_PREFIX: String = "http.sink"

  /**
   *
   * @param properties
   * @return
   */
  def apply(prefixStr: String = HTTP_SINK_PREFIX, properties: Properties = new Properties): HttpConfigOption = new HttpConfigOption(prefixStr, properties)

}

class HttpConfigOption(prefixStr: String, properties: Properties) {

  implicit val (prefix, prop) = (prefixStr, properties)

}


