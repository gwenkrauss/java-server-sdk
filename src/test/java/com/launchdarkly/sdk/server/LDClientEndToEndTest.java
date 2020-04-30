package com.launchdarkly.sdk.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;

import org.junit.Test;

import static com.launchdarkly.sdk.server.Components.noEvents;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.TestHttpUtil.basePollingConfig;
import static com.launchdarkly.sdk.server.TestHttpUtil.baseStreamingConfig;
import static com.launchdarkly.sdk.server.TestHttpUtil.httpsServerWithSelfSignedCert;
import static com.launchdarkly.sdk.server.TestHttpUtil.jsonResponse;
import static com.launchdarkly.sdk.server.TestHttpUtil.makeStartedServer;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

@SuppressWarnings("javadoc")
public class LDClientEndToEndTest {
  private static final Gson gson = new Gson();
  private static final String sdkKey = "sdk-key";
  private static final String flagKey = "flag1";
  private static final DataModel.FeatureFlag flag = flagBuilder(flagKey)
      .offVariation(0).variations(LDValue.of(true))
      .build();
  private static final LDUser user = new LDUser("user-key");
  
  @Test
  public void clientStartsInPollingMode() throws Exception {
    MockResponse resp = jsonResponse(makeAllDataJson());
    
    try (MockWebServer server = makeStartedServer(resp)) {
      LDConfig config = new LDConfig.Builder()
          .dataSource(basePollingConfig(server))
          .events(noEvents())
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertTrue(client.initialized());
        assertTrue(client.boolVariation(flagKey, user, false));
      }
    }
  }

  @Test
  public void clientFailsInPollingModeWith401Error() throws Exception {
    MockResponse resp = new MockResponse().setResponseCode(401);
    
    try (MockWebServer server = makeStartedServer(resp)) {
      LDConfig config = new LDConfig.Builder()
          .dataSource(basePollingConfig(server))
          .events(noEvents())
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertFalse(client.initialized());
        assertFalse(client.boolVariation(flagKey, user, false));
      }
    }
  }

  @Test
  public void clientStartsInPollingModeWithSelfSignedCert() throws Exception {
    MockResponse resp = jsonResponse(makeAllDataJson());
    
    try (TestHttpUtil.ServerWithCert serverWithCert = httpsServerWithSelfSignedCert(resp)) {
      LDConfig config = new LDConfig.Builder()
          .dataSource(basePollingConfig(serverWithCert.server))
          .events(noEvents())
          .http(Components.httpConfiguration().sslSocketFactory(serverWithCert.socketFactory, serverWithCert.trustManager))
          // allows us to trust the self-signed cert
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertTrue(client.initialized());
        assertTrue(client.boolVariation(flagKey, user, false));
      }
    }
  }

  @Test
  public void clientStartsInStreamingMode() throws Exception {
    String streamData = "event: put\n" +
        "data: {\"data\":" + makeAllDataJson() + "}\n\n";    
    MockResponse resp = TestHttpUtil.eventStreamResponse(streamData);
    
    try (MockWebServer server = makeStartedServer(resp)) {
      LDConfig config = new LDConfig.Builder()
          .dataSource(baseStreamingConfig(server))
          .events(noEvents())
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertTrue(client.initialized());
        assertTrue(client.boolVariation(flagKey, user, false));
      }
    }
  }

  @Test
  public void clientFailsInStreamingModeWith401Error() throws Exception {
    MockResponse resp = new MockResponse().setResponseCode(401);
    
    try (MockWebServer server = makeStartedServer(resp)) {
      LDConfig config = new LDConfig.Builder()
          .dataSource(baseStreamingConfig(server))
          .events(noEvents())
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertFalse(client.initialized());
        assertFalse(client.boolVariation(flagKey, user, false));
      }
    }
  }

  @Test
  public void clientStartsInStreamingModeWithSelfSignedCert() throws Exception {
    String streamData = "event: put\n" +
        "data: {\"data\":" + makeAllDataJson() + "}\n\n";    
    MockResponse resp = TestHttpUtil.eventStreamResponse(streamData);
    
    try (TestHttpUtil.ServerWithCert serverWithCert = httpsServerWithSelfSignedCert(resp)) {
      LDConfig config = new LDConfig.Builder()
          .dataSource(baseStreamingConfig(serverWithCert.server))
          .events(noEvents())
          .http(Components.httpConfiguration().sslSocketFactory(serverWithCert.socketFactory, serverWithCert.trustManager))
          // allows us to trust the self-signed cert
          .build();
      
      try (LDClient client = new LDClient(sdkKey, config)) {
        assertTrue(client.initialized());
        assertTrue(client.boolVariation(flagKey, user, false));
      }
    }
  }

  public String makeAllDataJson() {
    JsonObject flagsData = new JsonObject();
    flagsData.add(flagKey, gson.toJsonTree(flag));
    JsonObject allData = new JsonObject();
    allData.add("flags", flagsData);
    allData.add("segments", new JsonObject());
    return gson.toJson(allData);
  }
}