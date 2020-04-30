package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.DataModel;
import com.launchdarkly.sdk.server.DefaultFeatureRequestor;
import com.launchdarkly.sdk.server.FeatureRequestor;
import com.launchdarkly.sdk.server.HttpErrorException;
import com.launchdarkly.sdk.server.InMemoryDataStore;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.PollingProcessor;
import com.launchdarkly.sdk.server.integrations.PollingDataSourceBuilder;
import com.launchdarkly.sdk.server.interfaces.DataSourceFactory;
import com.launchdarkly.sdk.server.interfaces.DataStore;

import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static com.launchdarkly.sdk.server.TestComponents.dataStoreUpdates;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@SuppressWarnings("javadoc")
public class PollingProcessorTest {
  private static final String SDK_KEY = "sdk-key";
  private static final Duration LENGTHY_INTERVAL = Duration.ofSeconds(60);
  
  @Test
  public void builderHasDefaultConfiguration() throws Exception {
    DataSourceFactory f = Components.pollingDataSource();
    try (PollingProcessor pp = (PollingProcessor)f.createDataSource(clientContext(SDK_KEY, LDConfig.DEFAULT), null)) {
      assertThat(((DefaultFeatureRequestor)pp.requestor).baseUri, equalTo(LDConfig.DEFAULT_BASE_URI));
      assertThat(pp.pollInterval, equalTo(PollingDataSourceBuilder.DEFAULT_POLL_INTERVAL));
    }
  }

  @Test
  public void builderCanSpecifyConfiguration() throws Exception {
    URI uri = URI.create("http://fake");
    DataSourceFactory f = Components.pollingDataSource()
        .baseURI(uri)
        .pollInterval(LENGTHY_INTERVAL);
    try (PollingProcessor pp = (PollingProcessor)f.createDataSource(clientContext(SDK_KEY, LDConfig.DEFAULT), null)) {
      assertThat(((DefaultFeatureRequestor)pp.requestor).baseUri, equalTo(uri));
      assertThat(pp.pollInterval, equalTo(LENGTHY_INTERVAL));
    }
  }
  
  @Test
  public void testConnectionOk() throws Exception {
    MockFeatureRequestor requestor = new MockFeatureRequestor();
    requestor.allData = new FeatureRequestor.AllData(new HashMap<>(), new HashMap<>());
    DataStore store = new InMemoryDataStore();
    
    try (PollingProcessor pollingProcessor = new PollingProcessor(requestor, dataStoreUpdates(store), LENGTHY_INTERVAL)) {    
      Future<Void> initFuture = pollingProcessor.start();
      initFuture.get(1000, TimeUnit.MILLISECONDS);
      assertTrue(pollingProcessor.isInitialized());
      assertTrue(store.isInitialized());
    }
  }

  @Test
  public void testConnectionProblem() throws Exception {
    MockFeatureRequestor requestor = new MockFeatureRequestor();
    requestor.ioException = new IOException("This exception is part of a test and yes you should be seeing it.");
    DataStore store = new InMemoryDataStore();

    try (PollingProcessor pollingProcessor = new PollingProcessor(requestor, dataStoreUpdates(store), LENGTHY_INTERVAL)) {
      Future<Void> initFuture = pollingProcessor.start();
      try {
        initFuture.get(200L, TimeUnit.MILLISECONDS);
        fail("Expected Timeout, instead initFuture.get() returned.");
      } catch (TimeoutException ignored) {
      }
      assertFalse(initFuture.isDone());
      assertFalse(pollingProcessor.isInitialized());
      assertFalse(store.isInitialized());
    }
  }

  @Test
  public void http400ErrorIsRecoverable() throws Exception {
    testRecoverableHttpError(400);
  }
  
  @Test
  public void http401ErrorIsUnrecoverable() throws Exception {
    testUnrecoverableHttpError(401);
  }

  @Test
  public void http403ErrorIsUnrecoverable() throws Exception {
    testUnrecoverableHttpError(403);
  }

  @Test
  public void http408ErrorIsRecoverable() throws Exception {
    testRecoverableHttpError(408);
  }

  @Test
  public void http429ErrorIsRecoverable() throws Exception {
    testRecoverableHttpError(429);
  }

  @Test
  public void http500ErrorIsRecoverable() throws Exception {
    testRecoverableHttpError(500);
  }
  
  private void testUnrecoverableHttpError(int status) throws Exception {
    MockFeatureRequestor requestor = new MockFeatureRequestor();
    requestor.httpException = new HttpErrorException(status);
    DataStore store = new InMemoryDataStore();
    
    try (PollingProcessor pollingProcessor = new PollingProcessor(requestor, dataStoreUpdates(store), LENGTHY_INTERVAL)) {  
      long startTime = System.currentTimeMillis();
      Future<Void> initFuture = pollingProcessor.start();
      try {
        initFuture.get(10, TimeUnit.SECONDS);
      } catch (TimeoutException ignored) {
        fail("Should not have timed out");
      }
      assertTrue((System.currentTimeMillis() - startTime) < 9000);
      assertTrue(initFuture.isDone());
      assertFalse(pollingProcessor.isInitialized());
    }
  }
  
  private void testRecoverableHttpError(int status) throws Exception {
    MockFeatureRequestor requestor = new MockFeatureRequestor();
    requestor.httpException = new HttpErrorException(status);
    DataStore store = new InMemoryDataStore();
    
    try (PollingProcessor pollingProcessor = new PollingProcessor(requestor, dataStoreUpdates(store), LENGTHY_INTERVAL)) {
      Future<Void> initFuture = pollingProcessor.start();
      try {
        initFuture.get(200, TimeUnit.MILLISECONDS);
        fail("expected timeout");
      } catch (TimeoutException ignored) {
      }
      assertFalse(initFuture.isDone());
      assertFalse(pollingProcessor.isInitialized());
    }
  }
  
  private static class MockFeatureRequestor implements FeatureRequestor {
    AllData allData;
    HttpErrorException httpException;
    IOException ioException;
    
    public void close() throws IOException {}

    public DataModel.FeatureFlag getFlag(String featureKey) throws IOException, HttpErrorException {
      return null;
    }

    public DataModel.Segment getSegment(String segmentKey) throws IOException, HttpErrorException {
      return null;
    }

    public AllData getAllData() throws IOException, HttpErrorException {
      if (httpException != null) {
        throw httpException;
      }
      if (ioException != null) {
        throw ioException;
      }
      return allData;
    }
  }
}