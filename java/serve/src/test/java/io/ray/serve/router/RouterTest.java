package io.ray.serve.router;

import io.ray.api.ActorHandle;
import io.ray.api.ObjectRef;
import io.ray.api.Ray;
import io.ray.serve.BaseServeTest;
import io.ray.serve.DummyServeController;
import io.ray.serve.api.Serve;
import io.ray.serve.common.Constants;
import io.ray.serve.config.DeploymentConfig;
import io.ray.serve.config.RayServeConfig;
import io.ray.serve.deployment.DeploymentId;
import io.ray.serve.deployment.DeploymentVersion;
import io.ray.serve.deployment.DeploymentWrapper;
import io.ray.serve.generated.DeploymentLanguage;
import io.ray.serve.generated.DeploymentTargetInfo;
import io.ray.serve.generated.RequestMetadata;
import io.ray.serve.replica.RayServeWrappedReplica;
import io.ray.serve.replica.ReplicaContext;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.RandomStringUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

public class RouterTest {
  @Test
  public void test() {

    try {
      BaseServeTest.initRay();

      String deploymentName = "RouterTest";
      String replicaTag = deploymentName + "_replica";
      String actorName = replicaTag;
      String version = "v1";
      String appName = "app1";
      Map<String, String> config = new HashMap<>();
      config.put(RayServeConfig.LONG_POOL_CLIENT_ENABLED, "false");

      // Controller
      ActorHandle<DummyServeController> controllerHandle =
          Ray.actor(DummyServeController::new, "")
              .setName(Constants.SERVE_CONTROLLER_NAME)
              .remote();

      // Replica
      DeploymentConfig deploymentConfig =
          new DeploymentConfig().setDeploymentLanguage(DeploymentLanguage.JAVA);

      Object[] initArgs =
          new Object[] {deploymentName, replicaTag, new Object(), new HashMap<>(), appName};

      DeploymentWrapper deploymentWrapper =
          new DeploymentWrapper()
              .setName(deploymentName)
              .setDeploymentConfig(deploymentConfig)
              .setDeploymentVersion(new DeploymentVersion(version))
              .setDeploymentDef(ReplicaContext.class.getName())
              .setInitArgs(initArgs);

      ActorHandle<RayServeWrappedReplica> replicaHandle =
          Ray.actor(RayServeWrappedReplica::new, deploymentWrapper, replicaTag)
              .setName(actorName)
              .remote();
      Assert.assertTrue(replicaHandle.task(RayServeWrappedReplica::checkHealth).remote().get());

      // Set ReplicaContext
      Serve.setInternalReplicaContext(null, null, null, config, null);

      // Router
      Router router = new Router(controllerHandle, new DeploymentId(deploymentName, appName));
      DeploymentTargetInfo.Builder builder = DeploymentTargetInfo.newBuilder();
      builder.addReplicaNames(actorName).setIsAvailable(true);
      router.getReplicaSet().updateWorkerReplicas(builder.build());

      // assign
      RequestMetadata.Builder requestMetadata = RequestMetadata.newBuilder();
      requestMetadata.setRequestId(RandomStringUtils.randomAlphabetic(10));
      requestMetadata.setCallMethod("getDeploymentName");

      ObjectRef<Object> resultRef = router.assignRequest(requestMetadata.build(), null);
      Assert.assertEquals((String) resultRef.get(), deploymentName);
    } finally {
      BaseServeTest.clearAndShutdownRay();
    }
  }
}
