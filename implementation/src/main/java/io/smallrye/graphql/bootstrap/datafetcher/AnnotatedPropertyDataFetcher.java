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

package io.smallrye.graphql.bootstrap.datafetcher;

import org.jboss.jandex.Type;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.PropertyDataFetcher;
import io.smallrye.graphql.bootstrap.Annotations;

/**
 * Extending the default property data fetcher and take the annotations into account
 * 
 * @author Phillip Kruger (phillip.kruger@redhat.com)
 */
public class AnnotatedPropertyDataFetcher extends PropertyDataFetcher {
    private final TransformableDataFetcherHelper transformableDataFetcherHelper;

    public AnnotatedPropertyDataFetcher(String propertyName, Type type, Annotations annotations) {
        super(propertyName);
        transformableDataFetcherHelper = new TransformableDataFetcherHelper(type, annotations);
    }

    @Override
    public Object get(DataFetchingEnvironment environment) {
        Object o = super.get(environment);
        return transformableDataFetcherHelper.transform(o);
    }
}