package io.smallrye.graphql.execution.datafetcher.helper;

import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.microprofile.graphql.GraphQLException;

import io.smallrye.graphql.execution.Classes;
import io.smallrye.graphql.execution.datafetcher.CollectionCreator;
import io.smallrye.graphql.schema.model.Field;

/**
 * Help with the fields when fetching data.
 * 
 * @author Phillip Kruger (phillip.kruger@redhat.com)
 */
public abstract class AbstractHelper {

    protected AbstractHelper() {
    }

    /**
     * This gets called after the value has been recursively transformed.
     * 
     * @param fieldValue the transformed value
     * @param field the field as created when scanning
     * @return the return value
     */
    abstract Object afterRecursiveTransform(Object fieldValue, Field field) throws GraphQLException;

    /**
     * This do the transform of a 'leaf' value
     * 
     * @param argumentValue the value
     * @param field the field as scanned
     * @return transformed value
     */
    abstract Object singleTransform(Object argumentValue, Field field);

    /**
     * Here we actually do the transform. This method get called recursively in the case of arrays
     * 
     * 
     * @param inputValue the value we got from graphql-java or response from the method call
     * @param field details about the expected type created while scanning the code
     * @return the argumentValue in the correct type and transformed
     * @throws org.eclipse.microprofile.graphql.GraphQLException
     */
    Object recursiveTransform(Object inputValue, Field field)
            throws GraphQLException {

        if (inputValue == null) {
            return null;
        }

        String expectedType = field.getReference().getClassName();

        if (field.getArray().isPresent()) {
            // First handle the array if this is an array
            if (field.getArray().get().getType().equals(io.smallrye.graphql.schema.model.Array.Type.ARRAY)) {
                return recursiveTransformArray(inputValue, field);
            } else {
                return recursiveTransformCollection(inputValue, field);
            }
        } else if (Classes.isOptional(expectedType)) {
            // Also handle optionals
            return recursiveTransformOptional(inputValue, field);
            //TODO: move to transformer?
        } else if (Classes.isUUID(expectedType)) {
            return UUID.fromString(inputValue.toString());
        } else if (Classes.isURL(expectedType)) {
            return createURL(inputValue);
        } else if (Classes.isURI(expectedType)) {
            return createURI(inputValue);
        } else {
            // we need to transform before we make sure the type is correct
            inputValue = singleTransform(inputValue, field);
        }

        // Recursive transformation done.
        return afterRecursiveTransform(inputValue, field);

    }

    /**
     * This just creates a new array and add values to it by calling the recursiveTransform method.
     * This allows arrays of arrays and transformation inside arrays
     * Even without transformation, we need to go from arrayList to Array,
     * or arraylist of arraylist to array of array
     * 
     * @param <T> the type in the array
     * @param argumentValue the list as from graphql-java (always an arraylist)
     * @param field the field as created while scanning
     * @return an array with the transformed values in.
     * @throws GraphQLException
     */
    private <T> Object recursiveTransformArray(Object argumentValue, Field field) throws GraphQLException {

        Collection givenCollection = getGivenCollection(argumentValue, field);

        List convertedList = new ArrayList();

        for (Object objectInGivenCollection : givenCollection) {
            Field fieldInCollection = getFieldInField(field);
            Object objectInCollection = recursiveTransform(objectInGivenCollection,
                    fieldInCollection);
            convertedList.add(objectInCollection);
        }

        String classNameInCollection = field.getReference().getClassName();
        Class classInCollection = Classes.loadClass(classNameInCollection);

        return convertedList.toArray((T[]) Array.newInstance(classInCollection, givenCollection.size()));
    }

    /**
     * This just creates a new correct type collection and add values to it by calling the recursiveTransform method.
     * This allows collections of collections and transformation inside collections
     * Even without transformation, we need to go from arrayList to the correct collection type,
     * or arraylist of arraylist to collection of collection
     * 
     * @param argumentValue the list as from graphql-java (always an arraylist)
     * @param field the field as created while scanning
     * @return a collection with the transformed values in.
     * @throws GraphQLException
     */
    private Object recursiveTransformCollection(Object argumentValue, Field field) throws GraphQLException {
        Collection givenCollection = getGivenCollection(argumentValue, field);

        String collectionClassName = field.getArray().get().getClassName();

        Collection convertedCollection = CollectionCreator.newCollection(collectionClassName);

        for (Object objectInGivenCollection : givenCollection) {
            Field fieldInCollection = getFieldInField(field);
            Object objectInCollection = recursiveTransform(objectInGivenCollection,
                    fieldInCollection);
            convertedCollection.add(objectInCollection);
        }

        return convertedCollection;
    }

    /**
     * This is not yet specified by MicroProfile GraphQL, but we support it by also allowing transformation the optional
     * element.
     * 
     * @param argumentValue the value as from graphql-java
     * @param argument the argument as created while scanning
     * @return a optional with the transformed value in.
     * @throws GraphQLException
     */
    private Object recursiveTransformOptional(Object argumentValue, Field field) throws GraphQLException {
        // Check the type and maybe apply transformation
        Collection givenCollection = getGivenCollection(argumentValue, field);
        if (givenCollection.isEmpty()) {
            return Optional.empty();
        } else {
            Object o = givenCollection.iterator().next();
            return Optional.of(recursiveTransform(o, field));
        }

    }

    /**
     * Here we create an field from a field, but reducing the collection depth.
     * This will happen until we get to the field in the collection.
     * 
     * This 'clone' an array, but update the array information
     * 
     * @param owner the owner field
     * @return the new field
     */
    private Field getFieldInField(Field owner) {
        Field child = new Field(owner.getMethodName(),
                owner.getPropertyName(),
                owner.getName(),
                owner.getDescription(),
                owner.getReference());
        // not null
        if (owner.isNotNull()) {
            child.markNotNull();
        }
        // transform info
        child.setTransformInfo(owner.getTransformInfo());
        // default value
        child.setDefaultValue(owner.getDefaultValue());

        // array
        if (owner.getArray().isPresent()) {
            io.smallrye.graphql.schema.model.Array ownerArray = owner.getArray().get();

            int depth = ownerArray.getDepth() - 1;
            if (depth > 0) {
                // We still not at the end
                io.smallrye.graphql.schema.model.Array childArray = new io.smallrye.graphql.schema.model.Array(
                        ownerArray.getClassName(),
                        ownerArray.getType(), depth);
                child.setArray(Optional.of(childArray));
            }
        }

        return child;

    }

    private <T> Collection getGivenCollection(Object argumentValue, Field field) {
        if (Classes.isCollection(argumentValue)) {
            return (Collection) argumentValue;
        } else {
            return Arrays.asList((T[]) argumentValue);
        }
    }

    private URI createURI(Object inputValue) throws GraphQLException {
        try {
            return new URI(inputValue.toString());
        } catch (URISyntaxException e) {
            throw new GraphQLException(e);
        }
    }

    private URL createURL(Object inputValue) throws GraphQLException {
        try {
            return new URL(inputValue.toString());
        } catch (MalformedURLException e) {
            throw new GraphQLException(e);
        }
    }
}
