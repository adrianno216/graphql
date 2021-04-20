package io.smallrye.graphql.tests.client.dynamic;

import static io.smallrye.graphql.client.core.Document.document;
import static io.smallrye.graphql.client.core.Field.field;
import static io.smallrye.graphql.client.core.Operation.operation;
import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;
import javax.json.JsonObject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.smallrye.graphql.client.NamedClient;
import io.smallrye.graphql.client.core.Document;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;
import io.smallrye.graphql.client.dynamic.vertx.VertxDynamicGraphQLClient;
import io.vertx.core.MultiMap;

/**
 * Verify a named dynamic client injected via CDI and configured via MP Config properties
 */
@RunWith(Arquillian.class)
public class DynamicClientInjectionTest {

    @Deployment
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class, "client-injection-test.war")
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                // we need to set the context-path of the web app in advance so that we know what URL to put into microprofile-config.properties
                .addAsWebInfResource(new StringAsset("<Configure class=\"org.eclipse.jetty.webapp.WebAppContext\">\n" +
                        "  <Set name=\"contextPath\">/client-injection-test</Set>\n" +
                        "</Configure>"), "jetty-web.xml")
                // configuration of the named client
                .addAsResource(new StringAsset(
                        "smallrye.graphql.client.dummy/url=http://localhost:9090/client-injection-test/graphql\n" +
                                "smallrye.graphql.client.dummy/header/My-Custom-Header=Header-Value"),
                        "META-INF/microprofile-config.properties")
                .addClasses(DynamicClientApi.class, Dummy.class);
    }

    @Inject
    @NamedClient("dummy")
    private DynamicGraphQLClient client;

    @Test
    public void testInjectedClient() throws ExecutionException, InterruptedException {
        Document document = document(
                operation("SimpleQuery",
                        field("simple",
                                field("string"),
                                field("integer"))));
        JsonObject data = client.executeSync(document).getData();
        assertEquals("asdf", data.getJsonObject("simple").getString("string"));
        assertEquals(30, data.getJsonObject("simple").getInt("integer"));
    }

    // check that the injected client instance passes the HTTP header that was requested in the configuration
    // we don't actually call the server side here, just inspect the internals of the client instance
    @Test
    public void verifyHttpHeaders() throws NoSuchFieldException, IllegalAccessException {
        Field field = VertxDynamicGraphQLClient.class.getDeclaredField("headers");
        field.setAccessible(true);
        MultiMap headers = (MultiMap) field.get(client);
        Assert.assertEquals("Header-Value", headers.get("My-Custom-Header"));
    }

}
