package test.unit;

import static org.assertj.core.api.BDDAssertions.then;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.smallrye.graphql.client.typesafe.impl.reflection.TypeInfo;

class TypeInfoBehaviour {

    class Thing {
        List<OtherThing> things;
    }

    class OtherThing {
        String someValue;
    }

    @Test
    void nullableListField() throws Exception {
        TypeInfo foo = TypeInfo.of(Thing.class);
        TypeInfo thingsField = foo.fields().findFirst().get().getType().getItemType();
        then(thingsField.isNonNull()).isFalse();
    }
}
