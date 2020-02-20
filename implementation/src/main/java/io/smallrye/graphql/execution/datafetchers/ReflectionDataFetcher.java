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

package io.smallrye.graphql.execution.datafetchers;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.enterprise.inject.spi.CDI;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import org.eclipse.microprofile.graphql.GraphQLException;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherResult;
import graphql.execution.ExecutionPath;
import graphql.language.SourceLocation;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLScalarType;
import io.smallrye.graphql.execution.error.GraphQLExceptionWhileDataFetching;
import io.smallrye.graphql.schema.Argument;
import io.smallrye.graphql.schema.Classes;
import io.smallrye.graphql.schema.helper.CollectionHelper;
import io.smallrye.graphql.schema.type.scalar.TransformException;
import io.smallrye.graphql.schema.type.scalar.Transformable;

/**
 * Fetch data using Reflection
 * 
 * @author Phillip Kruger (phillip.kruger@redhat.com)
 */
public class ReflectionDataFetcher implements DataFetcher {
    private static final Logger LOG = Logger.getLogger(ReflectionDataFetcher.class.getName());

    private final Method method;
    private final Class declaringClass;
    private final Class returnType;
    private List<Argument> arguments;

    private final boolean hasArguments;

    private final Map<DotName, Jsonb> inputJsonbMap;
    private final Map<DotName, Map<String, Argument>> argumentMap;
    private final Map<DotName, GraphQLScalarType> scalarMap;
    private final CollectionHelper collectionHelper = new CollectionHelper();

    public ReflectionDataFetcher(MethodInfo methodInfo, List<Argument> arguments, Map<DotName, Jsonb> inputJsonbMap,
            Map<DotName, Map<String, Argument>> argumentMap,
            Map<DotName, GraphQLScalarType> scalarMap) {
        try {
            this.arguments = arguments;
            this.argumentMap = argumentMap;
            this.inputJsonbMap = inputJsonbMap;
            this.scalarMap = scalarMap;
            this.declaringClass = loadClass(methodInfo.declaringClass().name().toString());
            this.returnType = getReturnType(methodInfo);
            Class[] parameterClasses = getParameterClasses(arguments);
            this.hasArguments = parameterClasses.length != 0;

            if (hasArguments) {
                this.method = this.declaringClass.getMethod(methodInfo.name(), parameterClasses);
            } else {
                this.method = this.declaringClass.getMethod(methodInfo.name());
            }
        } catch (NoSuchMethodException | SecurityException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public Object get(DataFetchingEnvironment dfe) throws Exception {
        try {
            Object declaringObject = CDI.current().select(declaringClass).get();
            return method.invoke(declaringObject, getArguments(dfe).toArray());
        } catch (TransformException pe) {
            return pe.getDataFetcherResult(dfe);
        } catch (InvocationTargetException ite) {
            return handle(ite, dfe);
        }
    }

    private DataFetcherResult<Object> handle(InvocationTargetException ite, DataFetchingEnvironment dfe) throws Exception {
        Throwable throwable = ite.getCause();

        if (throwable == null) {
            throw new RuntimeException(ite);
        } else {
            if (throwable instanceof Error) {
                throw (Error) throwable;
            } else if (throwable instanceof GraphQLException) {
                GraphQLException graphQLException = (GraphQLException) throwable;
                return getPartialResult(dfe, graphQLException);
            } else {
                throw (Exception) throwable;
            }
        }
    }

    private DataFetcherResult<Object> getPartialResult(DataFetchingEnvironment dfe, GraphQLException graphQLException) {
        DataFetcherExceptionHandlerParameters handlerParameters = DataFetcherExceptionHandlerParameters
                .newExceptionParameters()
                .dataFetchingEnvironment(dfe)
                .exception(graphQLException)
                .build();

        SourceLocation sourceLocation = handlerParameters.getSourceLocation();
        ExecutionPath path = handlerParameters.getPath();
        GraphQLExceptionWhileDataFetching error = new GraphQLExceptionWhileDataFetching(path, graphQLException,
                sourceLocation);

        return DataFetcherResult.newResult()
                .data(graphQLException.getPartialResults())
                .error(error)
                .build();

    }

    private ArrayList getArguments(DataFetchingEnvironment dfe) throws GraphQLException {
        ArrayList argumentObjects = new ArrayList();
        for (Argument a : arguments) {
            Object argument = getArgument(dfe, a.getName());
            argumentObjects.add(toArgumentInputParameter(argument, a));
        }
        return argumentObjects;
    }

    private Object toArgumentInputParameter(Object argumentValue, Argument a) throws GraphQLException {
        Type type = a.getType();

        if (argumentValue != null) {
            Type.Kind kind = type.kind();
            if (kind.equals(Type.Kind.PRIMITIVE)) {
                return handlePrimative(argumentValue, a);
            } else if (kind.equals(Type.Kind.ARRAY)) {
                return handleArray(argumentValue, a);
            } else if (kind.equals(Type.Kind.PARAMETERIZED_TYPE) && isOptionalType(a.getArgumentClass())) {
                return handleOptional(argumentValue, a);
            } else if (kind.equals(Type.Kind.PARAMETERIZED_TYPE)) {
                return handleCollection(argumentValue, a);
            } else if (kind.equals(Type.Kind.CLASS)) {
                return handleClass(argumentValue, a);
            } else {
                return handleDefault(argumentValue, a, "Not sure what to do with this kind");
            }
            // TODO: Handle Generics
        }
        return handleDefault(argumentValue, a, "Argument is NULL");
    }

    private Object handlePrimative(Object argumentValue, Argument a) {
        // First make sure we have a primative type
        Class clazz = a.getArgumentClass();
        Class givenClass = argumentValue.getClass();
        if (!givenClass.isPrimitive()) {
            givenClass = Classes.toPrimativeClassType(givenClass);
        }
        if (givenClass.equals(clazz)) {
            return argumentValue;
        } else {
            return toScalar(argumentValue, a, clazz);
        }
    }

    private Object handleClass(Object argumentValue, Argument argument) throws GraphQLException {
        Class clazz = argument.getArgumentClass();
        Class givenClass = argumentValue.getClass();
        if (givenClass.equals(clazz)) {
            return argumentValue;
        } else if (Map.class.isAssignableFrom(argumentValue.getClass())) {
            return mapToPojo(Map.class.cast(argumentValue), argument);
        } else if (givenClass.equals(String.class)) {
            // We got a String, but not expecting one. Lets bind to Pojo with JsonB or transformation
            // This happens with @DefaultValue and Transformable (Passthrough) Scalars
            return objectToPojo(argumentValue, argument);
        } else {
            return toScalar(argumentValue, argument, clazz);
        }
    }

    private <T> Object handleArray(Object argumentValue, Argument a) throws GraphQLException {
        Class clazz = a.getArgumentClass();
        Type type = a.getType();
        Collection givenCollection = (Collection) argumentValue;
        Type typeInCollection = type.asArrayType().component();

        List convertedList = new ArrayList();

        Argument argumentInCollection = new Argument(typeInCollection.name().local(), typeInCollection,
                a.getAnnotations());

        for (Object o : givenCollection) {
            convertedList.add(toArgumentInputParameter(o, argumentInCollection));
        }

        return convertedList.toArray((T[]) Array.newInstance(clazz.getComponentType(), givenCollection.size()));
    }

    private Object handleCollection(Object argumentValue, Argument a) throws GraphQLException {
        Class clazz = a.getArgumentClass();
        Type type = a.getType();
        Collection convertedList = collectionHelper.getCorrectCollectionType(clazz);

        Collection givenCollection = (Collection) argumentValue;

        Type typeInCollection = type.asParameterizedType().arguments().get(0); // TODO: Check for empty list !
        Argument argumentInCollection = new Argument(typeInCollection.name().local(), typeInCollection,
                a.getAnnotations());

        for (Object o : givenCollection) {
            convertedList.add(toArgumentInputParameter(o, argumentInCollection));
        }

        return convertedList;
    }

    private Object handleOptional(Object argumentValue, Argument a) {
        // TODO: Check the type and maybe apply transformation
        // Type type = a.getType();
        if (argumentValue == null) {
            return Optional.empty();
        } else {
            Collection givenCollection = (Collection) argumentValue;
            if (givenCollection.isEmpty()) {
                return Optional.empty();
            } else {
                Object o = givenCollection.iterator().next();
                //Type typeInCollection = type.asParameterizedType().arguments().get(0);
                return Optional.of(o);
            }
        }
    }

    private boolean isOptionalType(Class type) {
        return type.equals(Optional.class);
    }

    private Object handleDefault(Object argumentValue, Argument argument, String message) {
        if (argumentValue == null)
            return null;
        Type type = argument.getType();
        LOG.warn(message + " | argument [" + argumentValue + "] of kind [" + argumentValue.getClass().getName()
                + "] but expecting kind [" + type.kind().name() + "]");
        return argumentValue;
    }

    private Object getArgument(DataFetchingEnvironment dfe, String name) {
        Object argument = dfe.getArgument(name);
        if (argument != null) {
            return argument;
        }
        Object source = dfe.getSource();
        if (source != null) {
            return source;
        }
        return null;
    }

    private Object toScalar(Object input, Argument argument, Class clazz) {
        GraphQLScalarType scalar = getScalarType(argument.getType());
        if (scalar != null) {
            try {
                // For transformable scalars.
                if (Transformable.class.isInstance(scalar)) {
                    Transformable transformable = Transformable.class.cast(scalar);
                    input = transformable.transform(input, argument);
                }
                return Classes.stringToScalar(input.toString(), clazz);

            } catch (NumberFormatException nfe) {
                throw new TransformException(nfe, scalar, argument.getName(), input.toString());
            }
        } else {
            return handleDefault(input, argument, "Expected type [" + clazz.getName() + "]");
        }
    }

    private Object objectToPojo(Object input, Argument argument) throws GraphQLException {
        Class clazz = argument.getArgumentClass();
        // For Objects (from @DefaultValue)
        Jsonb jsonb = getJsonbForType(argument.getType());
        if (jsonb != null) {
            return jsonb.fromJson(input.toString(), clazz);
        }
        // For transformable scalars.
        GraphQLScalarType scalar = getScalarType(argument.getType());
        if (scalar != null && Transformable.class.isInstance(scalar)) {
            Transformable transformable = Transformable.class.cast(scalar);
            Object transformed = transformable.transform(input, argument);
            return clazz.cast(transformed);
        }
        return input;
    }

    private Object mapToPojo(Map m, Argument argument) throws GraphQLException {
        String jsonString = toJsonString(m, argument);
        Jsonb jsonb = getJsonbForType(argument.getType());
        if (jsonb != null) {
            Object o = jsonb.fromJson(jsonString, argument.getArgumentClass());
            return o;
        }
        return m;
    }

    private String toJsonString(Map inputMap, Argument argument) throws GraphQLException {
        DotName className = DotName.createSimple(argument.getArgumentClass().getName());
        try (Jsonb jsonb = JsonbBuilder.create()) {

            // See if there are any formatting type annotations of this class definition.
            if (this.argumentMap.containsKey(className)) {
                // See if any of the input fields needs formatting.
                if (hasInputFieldsThatNeedsFormatting(className, inputMap)) {
                    Map<String, Argument> fieldsThatShouldBeFormatted = this.argumentMap.get(className);
                    Set<Map.Entry> inputValues = inputMap.entrySet();
                    for (Map.Entry keyValue : inputValues) {
                        String key = String.valueOf(keyValue.getKey());
                        if (fieldsThatShouldBeFormatted.containsKey(key)) {
                            Argument fieldArgument = fieldsThatShouldBeFormatted.get(key);
                            Object o = toArgumentInputParameter(keyValue.getValue(), fieldArgument);
                            inputMap.put(keyValue.getKey(), o);
                        }
                    }
                }
            }

            return jsonb.toJson(inputMap);
        } catch (Exception e) {
            LOG.warn("Could not close Jsonb");
            return null;
        }
    }

    private boolean hasInputFieldsThatNeedsFormatting(DotName className, Map input) {
        Set<String> fieldsThatShouldBeFormatted = this.argumentMap.get(className).keySet();
        for (String fieldName : fieldsThatShouldBeFormatted) {
            if (input.containsKey(fieldName)) {
                return true;
            }
        }
        return false;
    }

    private Jsonb getJsonbForType(Type type) {
        if (inputJsonbMap.containsKey(type.name())) {
            return inputJsonbMap.get(type.name());
        }
        return null;
    }

    private GraphQLScalarType getScalarType(Type type) {
        if (scalarMap.containsKey(type.name())) {
            return scalarMap.get(type.name());
        }
        return null;
    }

    private Class loadClass(String className) {
        ClassLoader classLoader = ReflectionDataFetcher.class.getClassLoader();
        Class clazz = null;
        try {
            clazz = classLoader.loadClass(className);
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException("Could not find class [" + className + "]", ex);
        }
        return clazz;
    }

    private Class getReturnType(MethodInfo methodInfo) {
        Type type = methodInfo.returnType();
        Type.Kind kind = type.kind();
        String typename = type.name().toString();
        if (kind.equals(Type.Kind.PRIMITIVE)) {
            return Classes.getPrimativeClassType(typename);
        } else {
            return loadClass(typename);
        }
    }

    private Class[] getParameterClasses(List<Argument> arguments) {
        List<Class> cl = new ArrayList<>();
        for (Argument argument : arguments) {
            cl.add(argument.getArgumentClass());
        }
        return cl.toArray(new Class[] {});
    }

}
