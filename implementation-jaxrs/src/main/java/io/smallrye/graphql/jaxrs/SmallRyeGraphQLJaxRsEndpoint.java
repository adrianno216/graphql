/*
 * Copyright 2020 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.smallrye.graphql.jaxrs;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaPrinter;
import io.smallrye.graphql.execution.ExecutionService;

/**
 * The endpoint for schema and execution
 * 
 * @author Phillip Kruger (phillip.kruger@redhat.com)
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class SmallRyeGraphQLJaxRsEndpoint {
    private static final Logger LOG = Logger.getLogger(SmallRyeGraphQLJaxRsEndpoint.class.getName());

    @Inject
    private ExecutionService executionService;

    @Inject
    private GraphQLSchema graphQLSchema;

    private String schema = null;

    @PostConstruct
    public void init() {
        if (graphQLSchema != null) {
            SchemaPrinter schemaPrinter = new SchemaPrinter();
            this.schema = schemaPrinter.print(graphQLSchema);
        } else {
            LOG.warning("Can not create GraphQL Schema (null)");
        }
    }

    @GET
    @Path("schema.graphql")
    public Response getSchema() {
        return Response.ok(schema).build();
    }

    @POST
    public Response execute(String input) {
        try (StringReader sr = new StringReader(input)) {
            final JsonReader jsonReader = Json.createReader(sr);
            JsonObject jsonInput = jsonReader.readObject();

            JsonObject outputJson = executionService.execute(jsonInput);
            if (outputJson != null) {
                try (StringWriter sw = new StringWriter()) {
                    final JsonWriter jsonWriter = Json.createWriter(sw);
                    jsonWriter.writeObject(outputJson);
                    sw.flush();
                    return Response.ok(sw.toString()).build();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
            return Response.noContent().build();
        }
    }
}
