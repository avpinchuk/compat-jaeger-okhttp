/*
 * Copyright (c) 2021, Alexander Pinchuk
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.github.avpinchuk.compat.jaeger.okhttp;

import io.jaegertracing.internal.exceptions.SenderException;
import io.jaegertracing.thrift.internal.senders.HttpSender;
import io.jaegertracing.thriftjava.Batch;
import io.jaegertracing.thriftjava.Process;
import io.jaegertracing.thriftjava.Span;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

public class HttpSenderTest extends JerseyTest {

    private static final String USER = "testUser";

    private static final String PASSWORD = "testPassword";

    private static final String BEARER = "testBearer";

    private final static List<Span> spans = new ArrayList<>(10);

    @BeforeClass
    public static void setUpClass() {
        for (int i = 0; i < 10; i++) {
            spans.add(new Span().setOperationName("operation" + i));
        }
    }

    @Override
    protected Application configure() {
        return new ResourceConfig().register(JaegerEndpoint.class);
    }

    @Test
    public void testSendSpans() throws SenderException {
        HttpSender sender = new HttpSender.Builder(target("/api/traces").getUri().toString()).build();
        sender.send(new Process("process"), spans);
    }

    @Test
    public void testSendSpansWithBasicAuth() throws SenderException {
        HttpSender sender = new HttpSender.Builder(target("/api/basic").getUri().toString())
                .withAuth(USER, PASSWORD).build();
        sender.send(new Process("process"), spans);

        // invalid user and password
        HttpSender sender2 = new HttpSender.Builder(target("/api/basic").getUri().toString())
                .withAuth("invalidUser", "invalidPassword").build();
        SenderException exception = assertThrows(SenderException.class,
                                                 () -> sender2.send(new Process("process"), spans));
        assertThat(exception.getMessage(), containsString("response 401"));
    }

    @Test
    public void testSendSpansWithBearerAuth() throws SenderException {
        HttpSender sender = new HttpSender.Builder(target("/api/bearer").getUri().toString())
                .withAuth(BEARER).build();
        sender.send(new Process("process"), spans);

        // invalid token
        HttpSender sender2 = new HttpSender.Builder(target("/api/bearer").getUri().toString())
                .withAuth("invalidBearer").build();
        SenderException exception = assertThrows(SenderException.class,
                                                 () -> sender2.send(new Process("process"), spans));
        assertThat(exception.getMessage(), containsString("response 401"));
    }

    @Test
    public void testInternalServerError() {
        HttpSender sender = new HttpSender.Builder(target("/api/error").getUri().toString()).build();
        SenderException exception = assertThrows(SenderException.class,
                                                 () -> sender.send(new Process("process"), spans));
        assertThat(exception.getMessage(), containsString("response 500"));
    }

    @Path("api")
    public static class JaegerEndpoint {

        @Path("traces")
        @POST
        @Consumes("application/x-thrift")
        public Response traces(@QueryParam("format") String format, byte[] bytes) {
            return processRequest(format, bytes);
        }

        @Path("basic")
        @POST
        @Consumes("application/x-thrift")
        public Response basic(@HeaderParam("Authorization") String auth, @QueryParam("format") String format,  byte[] bytes) {
            if (auth == null || !auth.startsWith("Basic")) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }

            String[] credentials = new String(Base64.getDecoder().decode(auth.split("\\s+")[1])).split(":");
            if (!USER.equals(credentials[0]) && ! PASSWORD.equals(credentials[1])) {
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }

            return processRequest(format, bytes);
        }

        @Path("bearer")
        @POST
        @Consumes("application/x-thrift")
        public Response bearer(@HeaderParam("Authorization") String auth, @QueryParam("format") String format, byte[] bytes) {
            if (auth == null || !auth.startsWith("Bearer")) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }

            String bearer = auth.split("\\s+")[1];
            if (!BEARER.equals(bearer)) {
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }

            return processRequest(format, bytes);
        }

        @Path("error")
        @POST
        @Consumes("application/x-thrift")
        public Response error() {
            return Response.serverError().build();
        }

        private Response processRequest(String format, byte[] bytes) {
            if (format == null || !format.equals("jaeger.thrift")) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }

            TDeserializer deserializer = new TDeserializer(new TBinaryProtocol.Factory());
            try {
                Batch batch = new Batch();
                deserializer.deserialize(batch, bytes);
                if (batch.getSpansSize() != spans.size()) {
                    return Response.status(Response.Status.BAD_REQUEST).build();
                }
                return Response.ok().build();
            } catch (TException e) {
                return Response.serverError().build();
            }
        }
    }
}
