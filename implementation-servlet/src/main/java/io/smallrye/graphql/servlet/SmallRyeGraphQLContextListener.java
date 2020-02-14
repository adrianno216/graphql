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

package io.smallrye.graphql.servlet;

import java.util.logging.Logger;

import javax.inject.Inject;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import io.smallrye.graphql.SmallRyeGraphQLBootstrap;

/**
 * SmallRyeGraphQLBootstrap the GraphQL Runtime
 * TODO: Check for config on index location
 * 
 * @author Phillip Kruger (phillip.kruger@redhat.com)
 */
@WebListener
public class SmallRyeGraphQLContextListener implements ServletContextListener {
    private static final Logger LOG = Logger.getLogger(SmallRyeGraphQLContextListener.class.getName());

    @Inject
    private SmallRyeGraphQLBootstrap bootstrap;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        // TODO: Check if the Jandex index is available.
        bootstrap.generateSchema();
        LOG.info("SmallRye GraphQL Server started");
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        LOG.info("SmallRye GraphQL Server stoped");
    }
}
