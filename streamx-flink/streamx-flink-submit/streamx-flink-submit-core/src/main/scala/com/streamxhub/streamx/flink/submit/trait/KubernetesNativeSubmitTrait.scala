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

package com.streamxhub.streamx.flink.submit.`trait`

import com.streamxhub.streamx.common.enums.{ExecutionMode, FlinkK8sRestExposedType}
import com.streamxhub.streamx.flink.kubernetes.IngressController
import com.streamxhub.streamx.flink.packer.pipeline.DockerImageBuildResponse
import com.streamxhub.streamx.flink.submit.bean._
import org.apache.commons.lang3.StringUtils
import org.apache.flink.api.common.JobID
import org.apache.flink.client.deployment.ClusterSpecification
import org.apache.flink.client.program.ClusterClient
import org.apache.flink.configuration._
import org.apache.flink.kubernetes.configuration.KubernetesConfigOptions
import org.apache.flink.kubernetes.configuration.KubernetesConfigOptions.ServiceExposedType
import org.apache.flink.kubernetes.{KubernetesClusterClientFactory, KubernetesClusterDescriptor}

import java.io.File
import javax.annotation.Nonnull
import scala.collection.mutable
import scala.language.postfixOps

/**
  * kubernetes native mode submit
  */
//noinspection DuplicatedCode
trait KubernetesNativeSubmitTrait extends FlinkSubmitTrait {

  private[submit] val fatJarCached = new mutable.HashMap[String, File]()

  override def setConfig(submitRequest: SubmitRequest, flinkConfig: Configuration): Unit = {
    // extract from submitRequest
    flinkConfig
      .safeSet(KubernetesConfigOptions.CLUSTER_ID, submitRequest.k8sSubmitParam.clusterId)
      .safeSet(KubernetesConfigOptions.NAMESPACE, submitRequest.k8sSubmitParam.kubernetesNamespace)
      .safeSet(
        KubernetesConfigOptions.REST_SERVICE_EXPOSED_TYPE,
        covertToServiceExposedType(submitRequest.k8sSubmitParam.flinkRestExposedType)
      )

    if (submitRequest.buildResult != null) {
      if (submitRequest.executionMode == ExecutionMode.KUBERNETES_NATIVE_APPLICATION) {
        val buildResult = submitRequest.buildResult.asInstanceOf[DockerImageBuildResponse]
        buildResult.podTemplatePaths.foreach(p => {
          flinkConfig
            .safeSet(KubernetesConfigOptions.KUBERNETES_POD_TEMPLATE, p._2)
            .safeSet(KubernetesConfigOptions.JOB_MANAGER_POD_TEMPLATE, p._2)
            .safeSet(KubernetesConfigOptions.TASK_MANAGER_POD_TEMPLATE, p._2)
        })
      }
    }

    if (flinkConfig.get(KubernetesConfigOptions.NAMESPACE).isEmpty) {
      flinkConfig.removeConfig(KubernetesConfigOptions.NAMESPACE)
    }

    logInfo(
      s"""
         |------------------------------------------------------------------
         |Effective submit configuration: $flinkConfig
         |------------------------------------------------------------------
         |""".stripMargin)
  }

  // Tip: Perhaps it would be better to let users freely specify the savepoint directory
  @throws[Exception] override def doCancel(cancelRequest: CancelRequest, flinkConfig: Configuration): CancelResponse = {

    require(
      StringUtils.isNotBlank(cancelRequest.clusterId),
      s"[flink-submit] stop flink job failed, clusterId is null, mode=${flinkConfig.get(DeploymentOptions.TARGET)}"
    )

    flinkConfig
      .safeSet(KubernetesConfigOptions.CLUSTER_ID, cancelRequest.clusterId)
      .safeSet(KubernetesConfigOptions.NAMESPACE, cancelRequest.kubernetesNamespace)

    var clusterDescriptor: KubernetesClusterDescriptor = null
    var client: ClusterClient[String] = null

    try {
      clusterDescriptor = getK8sClusterDescriptor(flinkConfig)
      client = clusterDescriptor.retrieve(flinkConfig.getString(KubernetesConfigOptions.CLUSTER_ID)).getClusterClient
      val jobID = JobID.fromHexString(cancelRequest.jobId)
      val actionResult = cancelJob(cancelRequest, jobID, client)
      IngressController.deleteIngress(cancelRequest.clusterId, cancelRequest.kubernetesNamespace)
      CancelResponse(actionResult)

    } catch {
      case e: Exception =>
        logger.error(s"[flink-submit] stop flink job failed, mode=${flinkConfig.get(DeploymentOptions.TARGET)}, cancelRequest=${cancelRequest}")
        throw e
    } finally {
      if (client != null) client.close()
      if (clusterDescriptor != null) clusterDescriptor.close()
    }
  }

  //noinspection DuplicatedCode
  /*
    tips:
    The default kubernetes cluster communication information will be obtained from ./kube/conf file.

    If you need to customize the kubernetes cluster context, such as multiple target kubernetes clusters
    or multiple kubernetes api-server accounts, there are two ways to achieve this：
     1. Get the KubernetesClusterDescriptor by manually, building the FlinkKubeClient and specify
         the kubernetes context contents in the FlinkKubeClient.
     2. Specify an explicit key kubernetes.config.file in flinkConfig instead of the default value.
    */
  def getK8sClusterDescriptorAndSpecification(flinkConfig: Configuration)
  : (KubernetesClusterDescriptor, ClusterSpecification) = {
    val clientFactory = new KubernetesClusterClientFactory()
    val clusterDescriptor = clientFactory.createClusterDescriptor(flinkConfig)
    val clusterSpecification = clientFactory.getClusterSpecification(flinkConfig)
    (clusterDescriptor, clusterSpecification)
  }

  def getK8sClusterDescriptor(flinkConfig: Configuration): KubernetesClusterDescriptor = {
    val clientFactory = new KubernetesClusterClientFactory()
    val clusterDescriptor = clientFactory.createClusterDescriptor(flinkConfig)
    clusterDescriptor
  }

  protected def flinkConfIdentifierInfo(@Nonnull conf: Configuration): String =
    s"executionMode=${conf.get(DeploymentOptions.TARGET)}, clusterId=${conf.get(KubernetesConfigOptions.CLUSTER_ID)}, " +
      s"namespace=${conf.get(KubernetesConfigOptions.NAMESPACE)}"


  private def covertToServiceExposedType(exposedType: FlinkK8sRestExposedType): ServiceExposedType = exposedType match {
    case FlinkK8sRestExposedType.ClusterIP => ServiceExposedType.ClusterIP
    case FlinkK8sRestExposedType.LoadBalancer => ServiceExposedType.LoadBalancer
    case FlinkK8sRestExposedType.NodePort => ServiceExposedType.NodePort
    case _ => ServiceExposedType.LoadBalancer
  }

  def getDefaultKubernetesConf(k8sConf: String): String = {
    if (k8sConf != null) File.separator.concat(k8sConf.replace("~", "root"))
    else "/root/.kube/conf"
  }
}
