package io.smallrye.graphql.index;

import javax.json.bind.annotation.JsonbDateFormat;
import javax.json.bind.annotation.JsonbProperty;
import javax.json.bind.annotation.JsonbTransient;

import org.eclipse.microprofile.graphql.Argument;
import org.eclipse.microprofile.graphql.DefaultValue;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.Id;
import org.eclipse.microprofile.graphql.Ignore;
import org.eclipse.microprofile.graphql.InputField;
import org.eclipse.microprofile.graphql.InputType;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.NonNull;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.graphql.Type;
import org.jboss.jandex.DotName;

/**
 * All the annotations we care about
 * 
 * @author Phillip Kruger (phillip.kruger@redhat.com)
 */
public interface Annotations {
    public static final DotName QUERY = DotName.createSimple(Query.class.getName());
    public static final DotName MUTATION = DotName.createSimple(Mutation.class.getName());
    public static final DotName INPUTTYPE = DotName.createSimple(InputType.class.getName());
    public static final DotName TYPE = DotName.createSimple(Type.class.getName());

    public static final DotName INPUTFIELD = DotName.createSimple(InputField.class.getName());

    public static final DotName ID = DotName.createSimple(Id.class.getName());
    public static final DotName DESCRIPTION = DotName.createSimple(Description.class.getName());
    public static final DotName JSONB_DATE_FORMAT = DotName.createSimple(JsonbDateFormat.class.getName());

    public static final DotName JSONB_PROPERTY = DotName.createSimple(JsonbProperty.class.getName());

    public static final DotName IGNORE = DotName.createSimple(Ignore.class.getName());
    public static final DotName JSONB_TRANSIENT = DotName.createSimple(JsonbTransient.class.getName());

    public static final DotName NON_NULL = DotName.createSimple(NonNull.class.getName());
    public static final DotName DEFAULT_VALUE = DotName.createSimple(DefaultValue.class.getName());
    public static final DotName BEAN_VALIDATION_NOT_NULL = DotName.createSimple("javax.validation.constraints.NotNull");
    public static final DotName BEAN_VALIDATION_NOT_EMPTY = DotName.createSimple("javax.validation.constraints.NotEmpty");
    public static final DotName BEAN_VALIDATION_NOT_BLANK = DotName.createSimple("javax.validation.constraints.NotBlank");

    public static final DotName ARGUMENT = DotName.createSimple(Argument.class.getName());
}
