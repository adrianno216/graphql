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
package io.smallrye.graphql.execution;

import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.graphql.ConfigKey;

/**
 * Configuration for GraphQL
 *
 * @author Phillip Kruger (phillip.kruger@redhat.com)
 */
@ApplicationScoped
public class GraphQLConfig {

    @Inject
    @ConfigProperty(name = ConfigKey.EXCEPTION_BLACK_LIST, defaultValue = ",")
    private List<String> blackList;

    @Inject
    @ConfigProperty(name = ConfigKey.EXCEPTION_WHITE_LIST, defaultValue = ",")
    private List<String> whiteList;

    @Inject
    @ConfigProperty(name = ConfigKey.DEFAULT_ERROR_MESSAGE, defaultValue = "Server Error")
    private String defaultErrorMessage;

    @Inject
    @ConfigProperty(name = "mp.graphql.printDataFetcherException", defaultValue = "false")
    private boolean printDataFetcherException;

    @Inject
    @ConfigProperty(name = "mp.graphql.allowGet", defaultValue = "false")
    private boolean allowGet;

    public String getDefaultErrorMessage() {
        return defaultErrorMessage;
    }

    public void setDefaultErrorMessage(String defaultErrorMessage) {
        this.defaultErrorMessage = defaultErrorMessage;
    }

    public boolean isPrintDataFetcherException() {
        return printDataFetcherException;
    }

    public void setPrintDataFetcherException(boolean printDataFetcherException) {
        this.printDataFetcherException = printDataFetcherException;
    }

    public List<String> getBlackList() {
        return blackList;
    }

    public void setBlackList(List<String> blackList) {
        this.blackList = blackList;
    }

    public List<String> getWhiteList() {
        return whiteList;
    }

    public void setWhiteList(List<String> whiteList) {
        this.whiteList = whiteList;
    }

    public boolean isAllowGet() {
        return allowGet;
    }

    public void setAllowGet(boolean allowGet) {
        this.allowGet = allowGet;
    }

}
